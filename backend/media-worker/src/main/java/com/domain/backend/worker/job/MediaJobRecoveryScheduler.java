package com.domain.backend.worker.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MediaJobRecoveryScheduler {

    private final MediaJobStateService stateService;

    public MediaJobRecoveryScheduler(MediaJobStateService stateService) {
        this.stateService = stateService;
    }

    @Scheduled(fixedDelayString = "${media.worker.poll-interval:5s}")
    public void recoverExpiredLeases() {
        stateService.recoverExpiredLeases();
    }
}
