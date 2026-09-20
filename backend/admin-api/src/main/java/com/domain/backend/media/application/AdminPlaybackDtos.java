package com.domain.backend.media.application;

public final class AdminPlaybackDtos {

    private AdminPlaybackDtos() {
    }

    public record AdminPreviewStartResponse(
            Long videoId,
            Long mediaPackageId,
            String manifestUrl,
            long durationMs,
            long signedUrlTtlSeconds
    ) {
    }
}
