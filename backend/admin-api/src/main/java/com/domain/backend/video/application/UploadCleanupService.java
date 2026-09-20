package com.domain.backend.video.application;

import com.domain.backend.video.domain.VideoFile;
import com.domain.backend.video.domain.VideoFileStatus;
import com.domain.backend.video.infrastructure.persistence.VideoFileRepository;
import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
import com.domain.backend.video.infrastructure.storage.StorageProperties;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UploadCleanupService {

    private final VideoFileRepository videoFileRepository;
    private final ObjectStorageClient storageClient;
    private final StorageProperties storageProperties;

    public UploadCleanupService(VideoFileRepository videoFileRepository, ObjectStorageClient storageClient,
                                StorageProperties storageProperties) {
        this.videoFileRepository = videoFileRepository;
        this.storageClient = storageClient;
        this.storageProperties = storageProperties;
    }

    public int expireOldIncompleteUploads() {
        Instant cutoff = Instant.now().minus(storageProperties.getIncompleteUploadExpiration());
        int expired = 0;
        List<VideoFile> candidates = videoFileRepository.findTop100ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                List.of(VideoFileStatus.PREPARING, VideoFileStatus.UPLOADING, VideoFileStatus.FAILED),
                cutoff
        );
        for (VideoFile videoFile : candidates) {
            if (videoFile.getMultipartUploadId() != null) {
                storageClient.abortMultipartUpload(videoFile.getObjectKey(), videoFile.getMultipartUploadId());
            }
            videoFileRepository.forceStatus(videoFile.getId(), VideoFileStatus.EXPIRED);
            expired++;
        }
        return expired;
    }
}
