package com.domain.backend.video.application;

import com.domain.backend.video.domain.VideoFile;
import com.domain.backend.video.domain.VideoFileStatus;
import com.domain.backend.media.application.MediaProcessingEnqueueService;
import com.domain.backend.video.infrastructure.persistence.VideoFileRepository;
import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class UploadReconciliationService {

    private final VideoFileRepository videoFileRepository;
    private final ObjectStorageClient storageClient;
    private final MediaProcessingEnqueueService enqueueService;

    public UploadReconciliationService(VideoFileRepository videoFileRepository, ObjectStorageClient storageClient,
                                       MediaProcessingEnqueueService enqueueService) {
        this.videoFileRepository = videoFileRepository;
        this.storageClient = storageClient;
        this.enqueueService = enqueueService;
    }

    public int recoverCompletingUploads(Instant staleBefore) {
        int recovered = 0;
        for (VideoFile videoFile : videoFileRepository.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                VideoFileStatus.COMPLETING,
                staleBefore
        )) {
            var metadata = storageClient.headObject(videoFile.getObjectKey());
            if (metadata.exists() && metadata.contentLength() == videoFile.getFileSize()) {
                enqueueService.markVideoFileCompletedAndEnqueue(videoFile.getId());
                recovered++;
            }
        }
        return recovered;
    }
}
