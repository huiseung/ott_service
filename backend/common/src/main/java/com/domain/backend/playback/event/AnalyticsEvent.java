package com.domain.backend.playback.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Immutable facts; rates and aggregate metrics belong to downstream analytics. */
public record AnalyticsEvent(
        UUID eventId, String eventType, int eventVersion, Instant occurredAt, Instant receivedAt,
        Long userId, String anonymousId, String sessionId, String producer, String platform,
        String playbackSessionId, Long sequence, Long contentId, Long videoId,
        Map<String, Object> payload, PlaybackContext playbackContext
) {
    /** Server-resolved catalog facts; never supplied by the browser. */
    public record PlaybackContext(long durationMs, Long episodeId, Integer seasonNumber,
                                  Integer episodeNumber, Long nextEpisodeId) {}
}
