package com.domain.backend.worker.process;

import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
import com.domain.backend.worker.job.ClaimedMediaJob;
import com.domain.backend.worker.job.MediaJobStateService;
import com.domain.backend.media.domain.MediaProcessingStage;
import org.springframework.stereotype.Component;

@Component
public class SourceDownloader {

    private final ObjectStorageClient storageClient;
    private final MediaJobStateService stateService;

    public SourceDownloader(ObjectStorageClient storageClient, MediaJobStateService stateService) {
        this.storageClient = storageClient;
        this.stateService = stateService;
    }

    public void download(ClaimedMediaJob job, String objectKey, ScratchWorkspace workspace) {
        try {
            stateService.updateStage(job, MediaProcessingStage.DOWNLOADING_SOURCE, 0);
            storageClient.downloadObject(objectKey, workspace.sourceFile());
            stateService.updateStage(job, MediaProcessingStage.PROBING, 5);
        } catch (RuntimeException e) {
            throw new MediaProcessingException("SOURCE_DOWNLOAD_FAILED", e.getMessage(), true);
        }
    }
}
