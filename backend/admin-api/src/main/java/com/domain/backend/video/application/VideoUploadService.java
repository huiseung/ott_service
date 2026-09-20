package com.domain.backend.video.application;

import com.domain.backend.video.application.VideoUploadDtos.AckPartsRequest;
import com.domain.backend.video.application.VideoUploadDtos.CreateVideoRequest;
import com.domain.backend.video.application.VideoUploadDtos.CreateVideoResponse;
import com.domain.backend.video.application.VideoUploadDtos.PresignPartsRequest;
import com.domain.backend.video.application.VideoUploadDtos.PresignPartsResponse;
import com.domain.backend.video.application.VideoUploadDtos.PresignedPartResponse;
import com.domain.backend.media.application.MediaProcessingEnqueueService;
import com.domain.backend.video.domain.UploadIdempotencyKey;
import com.domain.backend.video.domain.Video;
import com.domain.backend.video.domain.VideoFile;
import com.domain.backend.video.domain.VideoFileStatus;
import com.domain.backend.video.infrastructure.persistence.UploadIdempotencyKeyRepository;
import com.domain.backend.video.infrastructure.persistence.VideoFilePartRepository;
import com.domain.backend.video.infrastructure.persistence.VideoFileRepository;
import com.domain.backend.video.infrastructure.persistence.VideoRepository;
import com.domain.backend.video.infrastructure.storage.CompletedPart;
import com.domain.backend.video.infrastructure.storage.ListedPart;
import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
import com.domain.backend.video.infrastructure.storage.StorageProperties;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class VideoUploadService {

    private final VideoRepository videoRepository;
    private final VideoFileRepository videoFileRepository;
    private final VideoFilePartRepository partRepository;
    private final UploadIdempotencyKeyRepository idempotencyKeyRepository;
    private final MultipartPartSizePolicy partSizePolicy;
    private final ObjectStorageClient storageClient;
    private final StorageProperties storageProperties;
    private final RequestHasher requestHasher;
    private final MediaProcessingEnqueueService mediaProcessingEnqueueService;
    private final TransactionTemplate transactionTemplate;

    public VideoUploadService(VideoRepository videoRepository, VideoFileRepository videoFileRepository,
                              VideoFilePartRepository partRepository,
                              UploadIdempotencyKeyRepository idempotencyKeyRepository,
                              MultipartPartSizePolicy partSizePolicy, ObjectStorageClient storageClient,
                              StorageProperties storageProperties, RequestHasher requestHasher,
                              MediaProcessingEnqueueService mediaProcessingEnqueueService,
                              PlatformTransactionManager transactionManager) {
        this.videoRepository = videoRepository;
        this.videoFileRepository = videoFileRepository;
        this.partRepository = partRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.partSizePolicy = partSizePolicy;
        this.storageClient = storageClient;
        this.storageProperties = storageProperties;
        this.requestHasher = requestHasher;
        this.mediaProcessingEnqueueService = mediaProcessingEnqueueService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public CreateVideoResponse createVideoAndUpload(CreateVideoRequest request, String idempotencyKey) {
        validateCreateRequest(request);
        String requestHash = requestHasher.createHash(request);
        AtomicReference<String> createdUploadId = new AtomicReference<>();
        AtomicReference<String> createdObjectKey = new AtomicReference<>();
        try {
            Long videoFileId = inTransaction(() -> {
                Optional<UploadIdempotencyKey> existing = findExistingIdempotencyKey(idempotencyKey);
                if (existing.isPresent()) {
                    requireSameRequestHash(existing.get(), requestHash);
                    return existing.get().getVideoFileId();
                }

                VideoFile videoFile = createPreparingVideoFile(request);
                reserveIdempotencyKey(idempotencyKey, requestHash, videoFile);
                String uploadId = storageClient.createMultipartUpload(videoFile.getObjectKey(), videoFile.getContentType());
                createdUploadId.set(uploadId);
                createdObjectKey.set(videoFile.getObjectKey());
                videoFile.markUploading(uploadId);
                videoFileRepository.save(videoFile);
                return videoFile.getId();
            });
            return toCreateResponse(getVideoFile(videoFileId));
        } catch (DataIntegrityViolationException e) {
            abortCreatedMultipartUpload(createdObjectKey.get(), createdUploadId.get());
            return toCreateResponse(resolveConcurrentIdempotentRequest(idempotencyKey, requestHash, e));
        } catch (RuntimeException e) {
            abortCreatedMultipartUpload(createdObjectKey.get(), createdUploadId.get());
            throw e;
        }
    }

    public PresignPartsResponse presignParts(Long videoFileId, PresignPartsRequest request) {
        VideoFile videoFile = getVideoFile(videoFileId);
        requireStatus(videoFile, VideoFileStatus.UPLOADING);
        if (request.partNumbers().size() > storageProperties.getMaximumRequestedPresignedUrls()) {
            throw new UploadException(HttpStatus.BAD_REQUEST, "Too many part URLs requested");
        }
        var distinct = new HashSet<>(request.partNumbers());
        if (distinct.size() != request.partNumbers().size()) {
            throw new UploadException(HttpStatus.BAD_REQUEST, "Duplicate part numbers are not allowed");
        }
        for (Integer partNumber : request.partNumbers()) {
            validatePartNumber(videoFile, partNumber);
        }
        var presigned = request.partNumbers().stream()
                .map(partNumber -> storageClient.presignUploadPart(
                        videoFile.getObjectKey(),
                        videoFile.getMultipartUploadId(),
                        partNumber))
                .toList();
        Instant expiresAt = presigned.stream()
                .map(ObjectStorageClient.PresignedUploadPart::expiresAt)
                .min(Instant::compareTo)
                .orElse(Instant.now());
        return new PresignPartsResponse(
                presigned.stream()
                        .map(part -> new PresignedPartResponse(part.partNumber(), part.url().toString()))
                        .toList(),
                expiresAt
        );
    }

    public void acknowledgeParts(Long videoFileId, AckPartsRequest request) {
        inTransaction(() -> {
            VideoFile videoFile = getVideoFile(videoFileId);
            requireStatus(videoFile, VideoFileStatus.UPLOADING);
            for (var part : request.parts()) {
                validatePartNumber(videoFile, part.partNumber());
                partRepository.upsert(videoFileId, part.partNumber(), part.etag(), part.size(), Instant.now());
            }
            return null;
        });
    }

    public void complete(Long videoFileId) {
        VideoFile videoFile = getVideoFile(videoFileId);
        if (videoFile.getStatus() == VideoFileStatus.COMPLETED) {
            return;
        }
        if (videoFile.getStatus() == VideoFileStatus.UPLOADING) {
            if (transition(videoFileId, VideoFileStatus.UPLOADING, VideoFileStatus.COMPLETING) == 0) {
                complete(videoFileId);
                return;
            }
        } else if (videoFile.getStatus() != VideoFileStatus.COMPLETING) {
            throw new UploadException(HttpStatus.CONFLICT, "VideoFile is not completable");
        }

        try {
            VideoFile completing = getVideoFile(videoFileId);
            var existingObject = storageClient.headObject(completing.getObjectKey());
            if (existingObject.exists() && existingObject.contentLength() == completing.getFileSize()) {
                mediaProcessingEnqueueService.markVideoFileCompletedAndEnqueue(videoFileId);
                return;
            }
            List<com.domain.backend.video.domain.VideoFilePart> acknowledgedParts = loadAcknowledgedParts(completing);
            verifyAcknowledgedPartsExistInStorage(completing, acknowledgedParts);
            List<CompletedPart> parts = toCompletedParts(acknowledgedParts);
            storageClient.completeMultipartUpload(completing.getObjectKey(), completing.getMultipartUploadId(), parts);
            verifyFinalObject(completing);
            mediaProcessingEnqueueService.markVideoFileCompletedAndEnqueue(videoFileId);
        } catch (RuntimeException e) {
            transition(videoFileId, VideoFileStatus.COMPLETING, VideoFileStatus.UPLOADING);
            throw e;
        }
    }

    public void abort(Long videoFileId) {
        VideoFile videoFile = getVideoFile(videoFileId);
        if (videoFile.getStatus() == VideoFileStatus.COMPLETED) {
            throw new UploadException(HttpStatus.CONFLICT, "Completed VideoFile cannot be aborted");
        }
        if (videoFile.getStatus() == VideoFileStatus.ABORTED || videoFile.getStatus() == VideoFileStatus.EXPIRED) {
            return;
        }
        if (videoFile.getMultipartUploadId() != null) {
            storageClient.abortMultipartUpload(videoFile.getObjectKey(), videoFile.getMultipartUploadId());
        }
        markAborted(videoFileId);
    }

    private VideoFile createPreparingVideoFile(CreateVideoRequest request) {
        Video video = videoRepository.save(new Video(request.title()));
        var sizing = partSizePolicy.calculate(request.file().fileSize());
        int generation = 1;
        String objectKey = "originals/%d/%d/source%s".formatted(
                video.getId(),
                generation,
                fileExtension(request.file().fileName())
        );
        return videoFileRepository.save(new VideoFile(
                video,
                generation,
                request.file().fileName(),
                request.file().contentType(),
                request.file().fileSize(),
                request.file().fingerprint(),
                objectKey,
                sizing.partSize(),
                sizing.totalParts()
        ));
    }

    private void markAborted(Long videoFileId) {
        inTransaction(() -> {
            VideoFile videoFile = getVideoFile(videoFileId);
            videoFile.markAborted();
            videoFileRepository.save(videoFile);
            return null;
        });
    }

    private int transition(Long videoFileId, VideoFileStatus from, VideoFileStatus to) {
        return inTransaction(() -> videoFileRepository.updateStatus(videoFileId, from, to));
    }

    private List<com.domain.backend.video.domain.VideoFilePart> loadAcknowledgedParts(VideoFile videoFile) {
        var parts = partRepository.findByIdVideoFileIdOrderByIdPartNumberAsc(videoFile.getId());
        if (parts.size() != videoFile.getTotalParts()) {
            throw new UploadException(HttpStatus.CONFLICT, "Not all parts are acknowledged");
        }
        for (int i = 0; i < parts.size(); i++) {
            int expectedPart = i + 1;
            if (parts.get(i).getId().getPartNumber() != expectedPart) {
                throw new UploadException(HttpStatus.CONFLICT, "Missing acknowledged part " + expectedPart);
            }
        }
        return parts;
    }

    private List<CompletedPart> toCompletedParts(List<com.domain.backend.video.domain.VideoFilePart> parts) {
        return parts.stream()
                .map(part -> new CompletedPart(part.getId().getPartNumber(), part.getEtag()))
                .toList();
    }

    private void verifyAcknowledgedPartsExistInStorage(VideoFile videoFile,
                                                       List<com.domain.backend.video.domain.VideoFilePart> acknowledgedParts) {
        Map<Integer, ListedPart> uploadedParts = storageClient.listParts(
                        videoFile.getObjectKey(),
                        videoFile.getMultipartUploadId()
                ).stream()
                .collect(Collectors.toMap(ListedPart::partNumber, part -> part));
        for (var acknowledgedPart : acknowledgedParts) {
            int partNumber = acknowledgedPart.getId().getPartNumber();
            ListedPart uploadedPart = uploadedParts.get(partNumber);
            if (uploadedPart == null) {
                throw new UploadException(HttpStatus.CONFLICT, "Uploaded part is missing: " + partNumber);
            }
            if (!normalizeEtag(uploadedPart.etag()).equals(normalizeEtag(acknowledgedPart.getEtag()))
                    || uploadedPart.size() != acknowledgedPart.getSize()) {
                throw new UploadException(HttpStatus.CONFLICT, "Uploaded part does not match acknowledged part: " + partNumber);
            }
        }
    }

    private void verifyFinalObject(VideoFile videoFile) {
        var metadata = storageClient.headObject(videoFile.getObjectKey());
        if (!metadata.exists() || metadata.contentLength() != videoFile.getFileSize()) {
            throw new UploadException(HttpStatus.CONFLICT, "Completed object verification failed");
        }
    }

    private void validateCreateRequest(CreateVideoRequest request) {
        if (request.file().fileSize() > storageProperties.getMaximumFileSize().toBytes()) {
            throw new UploadException(HttpStatus.BAD_REQUEST, "File exceeds maximum allowed size");
        }
        if (!request.file().contentType().startsWith("video/")) {
            throw new UploadException(HttpStatus.BAD_REQUEST, "Only video MIME types are allowed");
        }
    }

    private void validatePartNumber(VideoFile videoFile, int partNumber) {
        if (partNumber < 1 || partNumber > videoFile.getTotalParts()) {
            throw new UploadException(HttpStatus.BAD_REQUEST, "Part number is out of range");
        }
    }

    private void requireStatus(VideoFile videoFile, VideoFileStatus status) {
        if (videoFile.getStatus() != status) {
            throw new UploadException(HttpStatus.CONFLICT, "VideoFile status must be " + status);
        }
    }

    private VideoFile getVideoFile(Long videoFileId) {
        return videoFileRepository.findById(videoFileId)
                .orElseThrow(() -> new UploadException(HttpStatus.NOT_FOUND, "VideoFile not found"));
    }

    private CreateVideoResponse toCreateResponse(VideoFile videoFile) {
        return new CreateVideoResponse(
                videoFile.getVideo().getId(),
                videoFile.getId(),
                videoFile.getPartSize(),
                videoFile.getTotalParts(),
                videoFile.getStatus()
        );
    }

    private String fileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot).toLowerCase();
    }

    private Optional<UploadIdempotencyKey> findExistingIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return Optional.empty();
        }
        return idempotencyKeyRepository.findById(idempotencyKey);
    }

    private void requireSameRequestHash(UploadIdempotencyKey existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new UploadException(HttpStatus.CONFLICT, "Idempotency-Key was already used for another request");
        }
    }

    private void reserveIdempotencyKey(String idempotencyKey, String requestHash, VideoFile videoFile) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return;
        }
        idempotencyKeyRepository.saveAndFlush(new UploadIdempotencyKey(
                idempotencyKey,
                requestHash,
                videoFile.getVideo().getId(),
                videoFile.getId()
        ));
    }

    private VideoFile resolveConcurrentIdempotentRequest(String idempotencyKey, String requestHash, RuntimeException original) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw original;
        }
        UploadIdempotencyKey existing = idempotencyKeyRepository.findById(idempotencyKey)
                .orElseThrow(() -> original);
        requireSameRequestHash(existing, requestHash);
        return getVideoFile(existing.getVideoFileId());
    }

    private void abortCreatedMultipartUpload(String objectKey, String uploadId) {
        if (objectKey == null || uploadId == null) {
            return;
        }
        try {
            storageClient.abortMultipartUpload(objectKey, uploadId);
        } catch (RuntimeException ignored) {
        }
    }

    private String normalizeEtag(String etag) {
        if (etag == null) {
            return "";
        }
        String normalized = etag.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private <T> T inTransaction(Supplier<T> supplier) {
        return transactionTemplate.execute(status -> supplier.get());
    }
}
