package com.domain.backend.video.application;

import com.domain.backend.video.infrastructure.storage.StorageProperties;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UploadMaintenanceScheduler {

    private final UploadReconciliationService reconciliationService;
    private final UploadCleanupService cleanupService;
    private final StorageProperties storageProperties;

    public UploadMaintenanceScheduler(UploadReconciliationService reconciliationService,
                                      UploadCleanupService cleanupService,
                                      StorageProperties storageProperties) {
        this.reconciliationService = reconciliationService;
        this.cleanupService = cleanupService;
        this.storageProperties = storageProperties;
    }

    @Scheduled(fixedDelayString = "${app.storage.reconciliation-interval:5m}")
    public void reconcileAndCleanup() {
        reconciliationService.recoverCompletingUploads(Instant.now().minus(storageProperties.getReconciliationInterval()));
        cleanupService.expireOldIncompleteUploads();
    }
}
