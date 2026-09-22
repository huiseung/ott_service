package com.domain.backend.playback.event;

import com.domain.backend.playback.domain.WatchEventType;
import java.time.Instant;
import java.util.UUID;

public record WatchEvent(
        UUID eventId,
        WatchEventType eventType,
        String playbackSessionId,
        Long playbackSessionDbId,
        Long userId,
        Long videoId,
        Long mediaPackageId,
        long positionSeconds,
        long durationSeconds,
        long sequence,
        Instant occurredAt,
        Instant publishedAt
) {
}
