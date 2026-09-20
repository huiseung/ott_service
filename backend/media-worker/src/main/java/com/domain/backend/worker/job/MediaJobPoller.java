package com.domain.backend.worker.job;

import com.domain.backend.worker.config.MediaWorkerProperties;
import com.domain.backend.worker.process.MediaProcessingService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MediaJobPoller {

    private final MediaWorkerProperties properties;
    private final MediaJobClaimService claimService;
    private final MediaProcessingService processingService;
    private final ExecutorService executor;
    private final Semaphore permits;

    public MediaJobPoller(MediaWorkerProperties properties, MediaJobClaimService claimService,
                          MediaProcessingService processingService) {
        this.properties = properties;
        this.claimService = claimService;
        this.processingService = processingService;
        this.executor = Executors.newFixedThreadPool(properties.getMaxConcurrentJobs());
        this.permits = new Semaphore(properties.getMaxConcurrentJobs());
    }

    @Scheduled(fixedDelayString = "${media.worker.poll-interval:5s}")
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }
        while (permits.tryAcquire()) {
            var claimed = claimService.claimNext();
            if (claimed.isEmpty()) {
                permits.release();
                return;
            }
            executor.submit(() -> {
                try {
                    processingService.process(claimed.get());
                } finally {
                    permits.release();
                }
            });
        }
    }
}
