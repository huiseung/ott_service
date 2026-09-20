package com.domain.backend.video.infrastructure.storage;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.NoSuchUploadException;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

public class S3ObjectStorageClient implements ObjectStorageClient {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final StorageProperties properties;
    private final Supplier<Instant> clock;

    public S3ObjectStorageClient(S3Client s3Client, S3Presigner presigner, StorageProperties properties,
                                 Supplier<Instant> clock) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String createMultipartUpload(String objectKey, String contentType) {
        var response = s3Client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .build());
        return response.uploadId();
    }

    @Override
    public PresignedUploadPart presignUploadPart(String objectKey, String uploadId, int partNumber) {
        var uploadPart = UploadPartRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();
        var presigned = presigner.presignUploadPart(UploadPartPresignRequest.builder()
                .signatureDuration(properties.getPresignedUrlTtl())
                .uploadPartRequest(uploadPart)
                .build());
        return new PresignedUploadPart(partNumber, presigned.url(), clock.get().plus(properties.getPresignedUrlTtl()));
    }

    @Override
    public List<ListedPart> listParts(String objectKey, String uploadId) {
        try {
            return s3Client.listPartsPaginator(request -> request
                            .bucket(properties.getBucket())
                            .key(objectKey)
                            .uploadId(uploadId))
                    .parts()
                    .stream()
                    .map(part -> new ListedPart(part.partNumber(), part.eTag(), part.size()))
                    .sorted(Comparator.comparingInt(ListedPart::partNumber))
                    .toList();
        } catch (NoSuchUploadException e) {
            return List.of();
        }
    }

    @Override
    public void completeMultipartUpload(String objectKey, String uploadId, List<CompletedPart> parts) {
        var completedParts = parts.stream()
                .sorted(Comparator.comparingInt(CompletedPart::partNumber))
                .map(part -> software.amazon.awssdk.services.s3.model.CompletedPart.builder()
                        .partNumber(part.partNumber())
                        .eTag(part.etag())
                        .build())
                .toList();
        s3Client.completeMultipartUpload(request -> request
                .bucket(properties.getBucket())
                .key(objectKey)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder()
                        .parts(completedParts)
                        .build()));
    }

    @Override
    public void abortMultipartUpload(String objectKey, String uploadId) {
        s3Client.abortMultipartUpload(request -> request
                .bucket(properties.getBucket())
                .key(objectKey)
                .uploadId(uploadId));
    }

    @Override
    public ObjectMetadata headObject(String objectKey) {
        try {
            var response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build());
            return new ObjectMetadata(true, response.contentLength());
        } catch (NoSuchKeyException e) {
            return ObjectMetadata.missing();
        } catch (SdkServiceException e) {
            if (e.statusCode() == 404) {
                return ObjectMetadata.missing();
            }
            throw e;
        }
    }

    @Override
    public void downloadObject(String objectKey, java.nio.file.Path destination) {
        s3Client.getObject(GetObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        .build(),
                ResponseTransformer.toFile(destination));
    }

    @Override
    public void putObject(String objectKey, java.nio.file.Path source, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromFile(source));
    }

    @Override
    public List<String> listObjectKeys(String prefix) {
        return s3Client.listObjectsV2Paginator(request -> request
                        .bucket(properties.getBucket())
                        .prefix(prefix))
                .contents()
                .stream()
                .map(object -> object.key())
                .toList();
    }

    @Override
    public void deleteObjects(List<String> objectKeys) {
        for (String objectKey : objectKeys) {
            s3Client.deleteObject(request -> request.bucket(properties.getBucket()).key(objectKey));
        }
    }

    @Override
    public String readObjectAsString(String objectKey) {
        ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .build());
        return bytes.asUtf8String();
    }

    @Override
    public byte[] readObjectBytes(String objectKey) {
        return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .build()).asByteArray();
    }

    @Override
    public StoredObject openObjectStream(String objectKey, String range) {
        var requestBuilder = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey);
        if (range != null && !range.isBlank()) {
            requestBuilder.range(range);
        }
        var stream = s3Client.getObject(requestBuilder.build());
        var response = stream.response();
        return new StoredObject(
                stream,
                response.contentLength(),
                response.contentRange(),
                response.contentRange() != null
        );
    }

    @Override
    public java.net.URL presignGetObject(String objectKey) {
        var getObject = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .build();
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(properties.getPresignedUrlTtl())
                .getObjectRequest(getObject)
                .build());
        return presigned.url();
    }
}
