package com.domain.backend.user.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class PlaybackDtos {

    private PlaybackDtos() {
    }

    public record PlaybackStartResponse(
            Long videoId,
            Long mediaPackageId,
            String playbackSessionToken,
            String manifestUrl,
            long durationMs,
            long resumePositionMs
    ) {
    }

    public record ProgressRequest(
            @Min(0) long positionMs,
            @Min(1) long durationMs,
            @Min(0) long clientEventSeq,
            @NotNull Instant occurredAt
    ) {
    }

    public record ProgressResponse(long positionMs, long clientEventSeq, Instant occurredAt) {
    }
}
