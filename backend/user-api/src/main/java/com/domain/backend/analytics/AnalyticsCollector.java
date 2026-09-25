package com.domain.backend.analytics;

import com.domain.backend.playback.domain.WatchEventType;
import com.domain.backend.playback.event.AnalyticsEvent;
import com.domain.backend.playback.event.WatchEvent;
import com.domain.backend.user.application.UserPrincipal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnalyticsCollector {
    private final AnalyticsPlaybackLookup playback;
    private final AnalyticsPublisher publisher;

    public AnalyticsCollector(AnalyticsPlaybackLookup playback, AnalyticsPublisher publisher) {
        this.playback = playback;
        this.publisher = publisher;
    }

    public record Collected(AnalyticsEvent event, WatchEvent progress) {}

    // No transaction may surround Kafka I/O. Lookup returns detached values before publishing.
    @Transactional(propagation = Propagation.NEVER)
    public CompletableFuture<AnalyticsDtos.Accepted> collect(AnalyticsDtos.Batch batch, UserPrincipal principal) {
        Instant receivedAt = Instant.now();
        Set<String> tokens = new HashSet<>();
        Set<UUID> ids = new HashSet<>();
        for (var event : batch.events()) {
            if (!ids.add(event.eventId())) bad("Duplicate eventId in batch");
            validatePayload(event);
            if (event.eventType().playback()) {
                if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
                if (event.playbackSessionId() == null || event.playbackSessionId().isBlank() || event.sequence() == null)
                    bad("Playback session and sequence are required");
                tokens.add(event.playbackSessionId());
            }
        }
        var sessions = tokens.isEmpty() ? Map.<String, AnalyticsPlaybackLookup.Session>of()
                : playback.find(tokens, principal.userId(), receivedAt);
        List<Collected> collected = new ArrayList<>();
        for (var input : batch.events()) {
            var session = input.playbackSessionId() == null ? null : sessions.get(input.playbackSessionId());
            if (input.eventType().playback() && session == null)
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Playback session is not accessible");
            if (session != null && ((input.videoId() != null && input.videoId() != session.videoId())
                    || (input.contentId() != null && !input.contentId().equals(session.contentId()))))
                bad("Playback identity does not match session");
            var raw = new AnalyticsEvent(input.eventId(), input.eventType().name(), input.eventVersion(),
                    input.occurredAt(), receivedAt, principal == null ? null : principal.userId(),
                    input.anonymousId(), input.sessionId(), "user-web", input.platform(),
                    input.playbackSessionId(), input.sequence(), session == null ? input.contentId() : session.contentId(),
                    session == null ? input.videoId() : Long.valueOf(session.videoId()), Map.copyOf(input.payload()),
                    session == null ? null : new AnalyticsEvent.PlaybackContext(session.durationMs(),
                            session.episodeId(), session.seasonNumber(), session.episodeNumber(), session.nextEpisodeId()));
            collected.add(new Collected(raw, progress(input, session, receivedAt)));
        }
        return publisher.publish(collected).thenApply(ignored ->
                new AnalyticsDtos.Accepted(collected.stream().map(value -> value.event().eventId()).toList()));
    }

    private WatchEvent progress(AnalyticsDtos.Event event, AnalyticsPlaybackLookup.Session session, Instant receivedAt) {
        if (session == null) return null;
        WatchEventType type = switch (event.eventType()) {
            case PLAY -> WatchEventType.PLAY;
            case HEARTBEAT, SEEK -> WatchEventType.PROGRESS;
            case PAUSE -> WatchEventType.PAUSE;
            case PLAYBACK_ENDED -> WatchEventType.COMPLETE;
            case PLAYBACK_SESSION_ENDED -> WatchEventType.SESSION_END;
            default -> null;
        };
        if (type == null) return null;
        if (Boolean.TRUE.equals(event.payload().get("ended"))) type = WatchEventType.COMPLETE;
        long duration = session.durationMs() / 1000;
        long position = Math.min(number(event.payload(), "positionMs").longValue() / 1000, duration);
        return new WatchEvent(event.eventId(), type, session.token(), session.id(), session.userId(),
                session.videoId(), session.mediaPackageId(), type == WatchEventType.COMPLETE ? duration : position,
                duration, event.sequence(), event.occurredAt(), receivedAt);
    }

    private void validatePayload(AnalyticsDtos.Event event) {
        for (var entry : event.payload().entrySet()) {
            if (entry.getKey().length() > 64 || entry.getValue() == null) bad("Invalid payload field");
            Object value = entry.getValue();
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean))
                bad("Payload values must be scalar facts");
            if (value instanceof String text && text.length() > 512) bad("Payload string too long");
            if (value instanceof Number n && !Double.isFinite(n.doubleValue())) bad("Invalid number");
        }
        if (event.eventType().playback()) {
            validateQoe(event);
            for (String field : List.of("positionMs", "previousPositionMs", "playedMsSincePreviousEvent", "durationMs")) {
                double value = number(event.payload(), field).doubleValue();
                if (value < 0 || value > 9_007_199_254_740_991d || value != Math.floor(value)) bad("Invalid " + field);
            }
            double rate = number(event.payload(), "playbackRate").doubleValue();
            if (rate <= 0 || rate > 16) bad("Invalid playbackRate");
            if (event.eventType() == AnalyticsDtos.EventType.SEEK) {
                if (number(event.payload(), "fromPositionMs").doubleValue() < 0
                        || number(event.payload(), "toPositionMs").doubleValue() < 0) bad("Invalid seek positions");
            }
        } else {
            if (event.playbackSessionId() != null || event.sequence() != null) bad("Unexpected playback context");
            if (event.eventType() != AnalyticsDtos.EventType.SEARCH && event.contentId() == null)
                bad("Content is required");
            if (event.eventType() == AnalyticsDtos.EventType.SEARCH
                    && !(event.payload().get("query") instanceof String)) bad("Search query is required");
        }
    }

    private static Number number(Map<String, Object> payload, String field) {
        if (payload.get(field) instanceof Number value && Double.isFinite(value.doubleValue())) return value;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing numeric " + field);
    }

    private void validateQoe(AnalyticsDtos.Event event) {
        var facts = event.payload();
        if (!facts.containsKey("qoeVersion")) return; // Legacy clients remain ingestible, but outside QoE metrics.
        if (number(facts, "qoeVersion").doubleValue() != 1) bad("Unsupported qoeVersion");
        if (event.eventType() == AnalyticsDtos.EventType.PLAY && facts.containsKey("startupTimeMs"))
            qoeDuration(facts, "startupTimeMs");
        if (event.eventType() == AnalyticsDtos.EventType.BUFFER_ENDED) qoeDuration(facts, "bufferingDurationMs");
        if (event.eventType() == AnalyticsDtos.EventType.PLAYBACK_ERROR) {
            if (!(facts.get("fatal") instanceof Boolean)) bad("Missing fatal flag");
            if (!(facts.get("source") instanceof String source) || !Set.of("media", "hls").contains(source)) bad("Invalid error source");
            if (!(facts.get("code") instanceof String code) || !code.matches("[A-Za-z0-9_]{1,64}")) bad("Invalid error code");
        }
    }

    private void qoeDuration(Map<String, Object> facts, String field) {
        double value = number(facts, field).doubleValue();
        if (value < 0 || value > 86_400_000 || value != Math.floor(value)) bad("Invalid " + field);
    }

    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
