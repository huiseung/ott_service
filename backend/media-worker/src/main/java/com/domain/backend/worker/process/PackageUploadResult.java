package com.domain.backend.worker.process;

public record PackageUploadResult(String packageUuid, String rootKey, String masterManifestKey, String playlistKey) {
}
