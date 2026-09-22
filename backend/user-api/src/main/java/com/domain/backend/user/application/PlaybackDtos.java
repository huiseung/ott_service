package com.domain.backend.user.application;

import com.domain.backend.playback.domain.WatchEventType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class PlaybackDtos {

    private PlaybackDtos() {
    }

    public record PlaybackStartResponse(
            Long videoId,
            Long mediaPackageId,
            String playbackSessionId,
            String hlsUrl,
            long durationSeconds,
            long resumePositionSeconds,
            Instant expiresAt
    ) {
    }

    public record WatchEventRequest(
            @NotNull WatchEventType eventType,
            @Min(0) long positionSeconds,
            @Min(0) long sequence,
            Instant occurredAt
    ) {
    }

    public record WatchEventResponse(String eventId, String status) {
    }
}
