package com.domain.backend.worker.process;

import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
import org.springframework.stereotype.Component;

@Component
public class OrphanPackageCleanupService {

    private final ObjectStorageClient storageClient;

    public OrphanPackageCleanupService(ObjectStorageClient storageClient) {
        this.storageClient = storageClient;
    }

    public void deletePackagePrefix(String rootKey) {
        try {
            var keys = storageClient.listObjectKeys(rootKey + "/");
            if (!keys.isEmpty()) {
                storageClient.deleteObjects(keys);
            }
        } catch (RuntimeException ignored) {
            // Object storage lifecycle remains the safety net for orphan outputs.
        }
    }
}
