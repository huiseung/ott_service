package com.domain.backend.analytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    public enum EventType {
        PLAYBACK_SESSION_STARTED, PLAY, PAUSE, HEARTBEAT, SEEK, BUFFER_STARTED, BUFFER_ENDED,
        QUALITY_CHANGED, PLAYBACK_RATE_CHANGED, PLAYBACK_ENDED, PLAYBACK_ERROR, PLAYBACK_SESSION_ENDED,
        CONTENT_IMPRESSION, CONTENT_CLICK, CONTENT_DETAIL_VIEW, SEARCH, WISHLIST_ADD,
        TRAILER_PLAY, SUBSCRIPTION_CTA_CLICK;

        public boolean playback() {
            return switch (this) {
                case PLAYBACK_SESSION_STARTED, PLAY, PAUSE, HEARTBEAT, SEEK, BUFFER_STARTED, BUFFER_ENDED,
                        QUALITY_CHANGED, PLAYBACK_RATE_CHANGED, PLAYBACK_ENDED, PLAYBACK_ERROR,
                        PLAYBACK_SESSION_ENDED -> true;
                default -> false;
            };
        }
    }

    public record Batch(@NotEmpty @Size(max = 50) List<@NotNull @Valid Event> events) {}

    public record Event(
            @NotNull UUID eventId,
            @NotNull EventType eventType,
            @Min(1) @Max(1) int eventVersion,
            @NotNull Instant occurredAt,
            @NotBlank @Size(max = 64) String anonymousId,
            @NotBlank @Size(max = 64) String sessionId,
            @NotNull @Pattern(regexp = "user-web") String producer,
            @NotNull @Pattern(regexp = "WEB") String platform,
            @Size(max = 64) String playbackSessionId,
            @PositiveOrZero Long sequence,
            @Positive Long contentId,
            @Positive Long videoId,
            @NotNull @Size(max = 24) Map<String, Object> payload
    ) {}

    public record Accepted(List<UUID> acceptedEventIds) {}
}
