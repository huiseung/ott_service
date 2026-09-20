package com.domain.backend.worker.process;

import com.domain.backend.media.domain.MediaProcessingStage;
import com.domain.backend.video.domain.VideoFile;
import com.domain.backend.video.infrastructure.persistence.VideoFileRepository;
import com.domain.backend.worker.config.MediaWorkerProperties;
import com.domain.backend.worker.job.ClaimedMediaJob;
import com.domain.backend.worker.job.MediaJobStateService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class MediaProcessingService {

    private final VideoFileRepository videoFileRepository;
    private final ScratchWorkspaceManager workspaceManager;
    private final SourceDownloader sourceDownloader;
    private final MediaProbe mediaProbe;
    private final MediaTranscoder transcoder;
    private final PackageUploader packageUploader;
    private final PackageValidator packageValidator;
    private final MediaPackagePublisher publisher;
    private final OrphanPackageCleanupService cleanupService;
    private final MediaJobStateService stateService;
    private final MediaWorkerProperties workerProperties;

    public MediaProcessingService(VideoFileRepository videoFileRepository,
                                  ScratchWorkspaceManager workspaceManager,
                                  SourceDownloader sourceDownloader,
                                  MediaProbe mediaProbe,
                                  MediaTranscoder transcoder,
                                  PackageUploader packageUploader,
                                  PackageValidator packageValidator,
                                  MediaPackagePublisher publisher,
                                  OrphanPackageCleanupService cleanupService,
                                  MediaJobStateService stateService,
                                  MediaWorkerProperties workerProperties) {
        this.videoFileRepository = videoFileRepository;
        this.workspaceManager = workspaceManager;
        this.sourceDownloader = sourceDownloader;
        this.mediaProbe = mediaProbe;
        this.transcoder = transcoder;
        this.packageUploader = packageUploader;
        this.packageValidator = packageValidator;
        this.publisher = publisher;
        this.cleanupService = cleanupService;
        this.stateService = stateService;
        this.workerProperties = workerProperties;
    }

    public void process(ClaimedMediaJob job) {
        ScratchWorkspace workspace = null;
        PackageUploadResult uploadResult = null;
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
        try {
            heartbeat.scheduleAtFixedRate(() -> {
                if (!stateService.heartbeat(job)) {
                    throw new IllegalStateException("Lost job ownership");
                }
            }, 0, workerProperties.getHeartbeatInterval().toMillis(), TimeUnit.MILLISECONDS);

            VideoFile source = videoFileRepository.findById(job.videoFileId())
                    .orElseThrow(() -> new MediaProcessingException("SOURCE_VIDEO_FILE_NOT_FOUND", "Source VideoFile not found", false));
            workspace = workspaceManager.prepare(job, source.getFileSize());
            sourceDownloader.download(job, source.getObjectKey(), workspace);

            stateService.updateStage(job, MediaProcessingStage.PROBING, 5);
            MediaProbeResult probeResult = mediaProbe.probeAndValidate(workspace.sourceFile());

            transcoder.transcode(job, workspace, probeResult);
            uploadResult = packageUploader.upload(job, workspace);
            packageValidator.validate(job, workspace, uploadResult, probeResult);

            var outcome = publisher.publish(job, uploadResult, probeResult);
            if (outcome != MediaPackagePublisher.PublishOutcome.PUBLISHED) {
                cleanupService.deletePackagePrefix(uploadResult.rootKey());
            }
            workspaceManager.cleanup(workspace, false);
        } catch (MediaProcessingException e) {
            if (uploadResult != null) {
                cleanupService.deletePackagePrefix(uploadResult.rootKey());
            }
            if (e.isRetryable()) {
                stateService.failRetryable(job, e.getErrorCode(), e.getMessage());
            } else {
                stateService.failPermanent(job, e.getErrorCode(), e.getMessage());
            }
            workspaceManager.cleanup(workspace, workerProperties.isRetainFailedScratch());
        } catch (RuntimeException e) {
            if (uploadResult != null) {
                cleanupService.deletePackagePrefix(uploadResult.rootKey());
            }
            stateService.failRetryable(job, "WORKER_INTERRUPTED", e.getMessage());
            workspaceManager.cleanup(workspace, workerProperties.isRetainFailedScratch());
        } finally {
            heartbeat.shutdownNow();
        }
    }
}
