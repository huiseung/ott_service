package com.domain.backend.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domain.backend.video.application.UploadCleanupService;
import com.domain.backend.video.application.UploadException;
import com.domain.backend.video.application.UploadReconciliationService;
import com.domain.backend.video.application.VideoUploadDtos.AckPartRequest;
import com.domain.backend.video.application.VideoUploadDtos.AckPartsRequest;
import com.domain.backend.video.application.VideoUploadDtos.CreateVideoRequest;
import com.domain.backend.video.application.VideoUploadDtos.FileRequest;
import com.domain.backend.video.application.VideoUploadDtos.PresignPartsRequest;
import com.domain.backend.video.application.VideoUploadQueryService;
import com.domain.backend.video.application.VideoUploadService;
import com.domain.backend.video.domain.VideoFileStatus;
import com.domain.backend.video.infrastructure.persistence.VideoFilePartRepository;
import com.domain.backend.video.infrastructure.persistence.VideoFileRepository;
import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
import com.domain.backend.media.domain.MediaProcessingJob;
import com.domain.backend.media.infrastructure.persistence.MediaProcessingJobRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class VideoUploadIntegrationTest {

    private static final String BUCKET = "ott-originals-test";
    private static final byte[] PART_1 = new byte[5 * 1024 * 1024];
    private static final byte[] PART_2 = new byte[1024 * 1024];

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ott_service")
            .withUsername("ott")
            .withPassword("ott");

    @Container
    static MinIOContainer minio = new MinIOContainer(DockerImageName
            .parse("quay.io/minio/minio:RELEASE.2024-01-16T16-07-38Z")
            .asCompatibleSubstituteFor("minio/minio"));

    @Autowired
    VideoUploadService uploadService;

    @Autowired
    VideoUploadQueryService queryService;

    @Autowired
    UploadReconciliationService reconciliationService;

    @Autowired
    UploadCleanupService cleanupService;

    @Autowired
    VideoFileRepository videoFileRepository;

    @Autowired
    VideoFilePartRepository partRepository;

    @Autowired
    ObjectStorageClient storageClient;

    @Autowired
    MediaProcessingJobRepository jobRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("app.security.admin-password", () -> "test-admin-password");
        registry.add("app.storage.bucket", () -> BUCKET);
        registry.add("app.storage.endpoint", minio::getS3URL);
        registry.add("app.storage.public-endpoint", minio::getS3URL);
        registry.add("app.storage.access-key", minio::getUserName);
        registry.add("app.storage.secret-key", minio::getPassword);
        registry.add("app.storage.minimum-part-size", () -> "5MB");
        registry.add("app.storage.maximum-part-size", () -> "5MB");
        registry.add("app.storage.maximum-file-size", () -> "50MB");
        registry.add("app.storage.reconciliation-interval", () -> "1m");
        registry.add("app.storage.incomplete-upload-expiration", () -> "1ms");
    }

    @BeforeAll
    static void createBucket() {
        try (S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(minio.getS3URL()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .build()) {
            RuntimeException lastFailure = null;
            for (int attempt = 1; attempt <= 10; attempt++) {
                try {
                    s3.createBucket(request -> request.bucket(BUCKET));
                    return;
                } catch (RuntimeException e) {
                    lastFailure = e;
                    sleep(500);
                }
            }
            throw lastFailure;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for MinIO", e);
        }
    }

    @Test
    void normalFlowCompletesFinalObject() throws Exception {
        var created = createUpload("normal-flow");
        String etag1 = putPart(created.videoFileId(), 1, PART_1);
        String etag2 = putPart(created.videoFileId(), 2, PART_2);

        uploadService.acknowledgeParts(created.videoFileId(), new AckPartsRequest(List.of(
                new AckPartRequest(1, etag1, PART_1.length),
                new AckPartRequest(2, etag2, PART_2.length)
        )));
        uploadService.complete(created.videoFileId());

        var videoFile = videoFileRepository.findById(created.videoFileId()).orElseThrow();
        assertThat(videoFile.getStatus()).isEqualTo(VideoFileStatus.COMPLETED);
        assertThat(storageClient.headObject(videoFile.getObjectKey()).contentLength()).isEqualTo(PART_1.length + PART_2.length);
        assertThat(jobRepository.countByJobKey(MediaProcessingJob.jobKey(created.videoFileId(), MediaProcessingJob.DEFAULT_PROFILE_VERSION)))
                .isEqualTo(1);
    }

    @Test
    void duplicateAckKeepsSinglePartRow() throws Exception {
        var created = createUpload("duplicate-ack");
        String etag = putPart(created.videoFileId(), 1, PART_1);

        var ack = new AckPartsRequest(List.of(new AckPartRequest(1, etag, PART_1.length)));
        uploadService.acknowledgeParts(created.videoFileId(), ack);
        uploadService.acknowledgeParts(created.videoFileId(), ack);

        assertThat(partRepository.countByIdVideoFileId(created.videoFileId())).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyReturnsExistingUpload() {
        var first = createUpload("same-idempotency");
        var second = createUpload("same-idempotency");

        assertThat(second.videoId()).isEqualTo(first.videoId());
        assertThat(second.videoFileId()).isEqualTo(first.videoFileId());
        assertThat(second.status()).isEqualTo(VideoFileStatus.UPLOADING);
    }

    @Test
    void browserCrashWithoutAckReturnsRetryPart() throws Exception {
        var created = createUpload("browser-crash");
        putPart(created.videoFileId(), 1, PART_1);

        var status = queryService.getUploadStatus(created.videoFileId());

        assertThat(status.confirmedParts()).isEmpty();
        assertThat(status.retryParts()).contains(1);
    }

    @Test
    void completeCanBeRetried() throws Exception {
        var created = createAndAckAll("complete-retry");

        uploadService.complete(created.videoFileId());
        uploadService.complete(created.videoFileId());

        assertThat(videoFileRepository.findById(created.videoFileId()).orElseThrow().getStatus())
                .isEqualTo(VideoFileStatus.COMPLETED);
        assertThat(jobRepository.countByJobKey(MediaProcessingJob.jobKey(created.videoFileId(), MediaProcessingJob.DEFAULT_PROFILE_VERSION)))
                .isEqualTo(1);
    }

    @Test
    void completeFailureRestoresUploadingSoPartsCanBeAcknowledgedAgain() throws Exception {
        var created = createUpload("complete-bad-ack");
        String etag1 = putPart(created.videoFileId(), 1, PART_1);
        String etag2 = putPart(created.videoFileId(), 2, PART_2);
        uploadService.acknowledgeParts(created.videoFileId(), new AckPartsRequest(List.of(
                new AckPartRequest(1, etag1, PART_1.length),
                new AckPartRequest(2, "\"wrong-etag\"", PART_2.length)
        )));

        assertThatThrownBy(() -> uploadService.complete(created.videoFileId()))
                .isInstanceOf(UploadException.class)
                .hasMessageContaining("Uploaded part does not match acknowledged part");
        assertThat(videoFileRepository.findById(created.videoFileId()).orElseThrow().getStatus())
                .isEqualTo(VideoFileStatus.UPLOADING);

        uploadService.acknowledgeParts(created.videoFileId(), new AckPartsRequest(List.of(
                new AckPartRequest(2, etag2, PART_2.length)
        )));
        uploadService.complete(created.videoFileId());

        assertThat(videoFileRepository.findById(created.videoFileId()).orElseThrow().getStatus())
                .isEqualTo(VideoFileStatus.COMPLETED);
    }

    @Test
    void reconciliationRecoversCompletedObjectWithCompletingDbStatus() throws Exception {
        var created = createAndAckAll("reconcile-complete");
        uploadService.complete(created.videoFileId());
        videoFileRepository.forceStatus(created.videoFileId(), VideoFileStatus.COMPLETING);

        int recovered = reconciliationService.recoverCompletingUploads(Instant.now().plusSeconds(1));

        assertThat(recovered).isEqualTo(1);
        assertThat(videoFileRepository.findById(created.videoFileId()).orElseThrow().getStatus())
                .isEqualTo(VideoFileStatus.COMPLETED);
        assertThat(jobRepository.countByJobKey(MediaProcessingJob.jobKey(created.videoFileId(), MediaProcessingJob.DEFAULT_PROFILE_VERSION)))
                .isEqualTo(1);
    }

    @Test
    void cleanupExpiresOldMultipartUploads() {
        var created = createUpload("cleanup");

        int expired = cleanupService.expireOldIncompleteUploads();

        assertThat(expired).isPositive();
        assertThat(videoFileRepository.findById(created.videoFileId()).orElseThrow().getStatus())
                .isEqualTo(VideoFileStatus.EXPIRED);
    }

    private com.domain.backend.video.application.VideoUploadDtos.CreateVideoResponse createAndAckAll(String title) throws Exception {
        var created = createUpload(title);
        String etag1 = putPart(created.videoFileId(), 1, PART_1);
        String etag2 = putPart(created.videoFileId(), 2, PART_2);
        uploadService.acknowledgeParts(created.videoFileId(), new AckPartsRequest(List.of(
                new AckPartRequest(1, etag1, PART_1.length),
                new AckPartRequest(2, etag2, PART_2.length)
        )));
        return created;
    }

    private com.domain.backend.video.application.VideoUploadDtos.CreateVideoResponse createUpload(String title) {
        return uploadService.createVideoAndUpload(new CreateVideoRequest(
                title,
                new FileRequest(title + ".mp4", PART_1.length + PART_2.length, "video/mp4", "fp-" + title)
        ), "idem-" + title);
    }

    private String putPart(Long videoFileId, int partNumber, byte[] bytes) throws Exception {
        var presigned = uploadService.presignParts(videoFileId, new PresignPartsRequest(List.of(partNumber)));
        var url = presigned.parts().getFirst().url();
        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                        .build(),
                HttpResponse.BodyHandlers.discarding()
        );
        assertThat(response.statusCode()).isBetween(200, 299);
        return response.headers().firstValue("ETag").orElseThrow();
    }
}
