package com.domain.backend.worker.process;

import com.domain.backend.media.domain.MediaProcessingStage;
import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
import com.domain.backend.worker.config.MediaProfileProperties;
import com.domain.backend.worker.config.MediaWorkerProperties;
import com.domain.backend.worker.job.ClaimedMediaJob;
import com.domain.backend.worker.job.MediaJobStateService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class PackageUploader {

    private final ObjectStorageClient storageClient;
    private final MediaProfileProperties profile;
    private final MediaWorkerProperties workerProperties;
    private final MediaJobStateService stateService;

    public PackageUploader(ObjectStorageClient storageClient, MediaProfileProperties profile,
                           MediaWorkerProperties workerProperties, MediaJobStateService stateService) {
        this.storageClient = storageClient;
        this.profile = profile;
        this.workerProperties = workerProperties;
        this.stateService = stateService;
    }

    public PackageUploadResult upload(ClaimedMediaJob job, ScratchWorkspace workspace) {
        stateService.updateStage(job, MediaProcessingStage.UPLOADING_PACKAGE, 90);
        String packageUuid = UUID.randomUUID().toString();
        String rootKey = "videos/%d/packages/%s".formatted(job.videoId(), packageUuid);
        ExecutorService executor = null;
        try {
            List<Path> files;
            try (var stream = Files.walk(workspace.outputDirectory())) {
                files = stream.filter(Files::isRegularFile).toList();
            }
            executor = Executors.newFixedThreadPool(workerProperties.getPackageUploadConcurrency());
            List<Future<?>> futures = new ArrayList<>();
            List<RuntimeException> failures = java.util.Collections.synchronizedList(new ArrayList<>());
            for (Path file : files) {
                futures.add(executor.submit(() -> {
                    try {
                        String relative = workspace.outputDirectory().relativize(file).toString().replace('\\', '/');
                        storageClient.putObject(rootKey + "/" + relative, file, contentType(file));
                    } catch (RuntimeException e) {
                        failures.add(e);
                    }
                }));
            }
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.MINUTES) || !failures.isEmpty()) {
                futures.forEach(future -> future.cancel(true));
                executor.shutdownNow();
                throw new MediaProcessingException("PACKAGE_UPLOAD_FAILED", "Failed to upload HLS package", true);
            }
            return new PackageUploadResult(
                    packageUuid,
                    rootKey,
                    rootKey + "/master.m3u8",
                    rootKey + "/" + profile.getRenditionName() + "/index.m3u8"
            );
        } catch (IOException e) {
            throw new MediaProcessingException("PACKAGE_UPLOAD_FAILED", e.getMessage(), true);
        } catch (InterruptedException e) {
            if (executor != null) {
                executor.shutdownNow();
            }
            Thread.currentThread().interrupt();
            throw new MediaProcessingException("WORKER_INTERRUPTED", e.getMessage(), true);
        }
    }

    private String contentType(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (name.endsWith(".ts")) {
            return "video/mp2t";
        }
        return "application/octet-stream";
    }
}
