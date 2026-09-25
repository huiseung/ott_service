package com.domain.backend.analyticsjob;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/** Normalized watch contributions; no seek distance or client completion score is accepted. */
public final class PlaybackObservationProjection {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
    private static final Set<String> TYPES = Set.of("PLAYBACK_SESSION_STARTED", "PLAY", "PAUSE", "HEARTBEAT",
            "SEEK", "BUFFER_STARTED", "BUFFER_ENDED", "QUALITY_CHANGED", "PLAYBACK_RATE_CHANGED",
            "PLAYBACK_ENDED", "PLAYBACK_ERROR", "PLAYBACK_SESSION_ENDED");

    public static String project(String raw) throws JsonProcessingException {
        JsonNode event = JSON.readTree(raw);
        if (event == null || !event.isObject()) throw new IllegalArgumentException("Invalid envelope");
        if (!event.path("eventVersion").isIntegralNumber() || !event.path("eventVersion").canConvertToInt()
                || event.path("eventVersion").intValue() != 1) return null;
        String type = text(event, "eventType");
        if (!TYPES.contains(type)) return null;
        String id = text(event, "eventId");
        if (!UUID.fromString(id).toString().equalsIgnoreCase(id)) throw new IllegalArgumentException("Invalid UUID");
        Instant time = Instant.parse(text(event, "occurredAt"));
        if (time.isBefore(Instant.EPOCH) || !time.isBefore(Instant.parse("2300-01-01T00:00:00Z")))
            throw new IllegalArgumentException("Invalid timestamp");
        JsonNode facts = event.path("payload");
        JsonNode context = event.path("playbackContext");
        long duration = context.isObject() ? number(context, "durationMs") : number(facts, "durationMs");
        long position = number(facts, "positionMs");
        long previous = number(facts, "previousPositionMs");
        long played = number(facts, "playedMsSincePreviousEvent");
        double rate = facts.path("playbackRate").asDouble(Double.NaN);
        if (!Double.isFinite(rate) || rate <= 0 || rate > 16) throw new IllegalArgumentException("Invalid rate");
        long end = Math.min(duration, type.equals("SEEK") ? number(facts, "fromPositionMs") : position);
        long start = Math.min(duration, previous);
        // Endpoint span capped by measured advancing time: jumps cannot fabricate a watched interval.
        long covered = Math.min(Math.max(0, end - start), (long) Math.floor(played * rate));
        if (type.equals("PLAYBACK_SESSION_STARTED")) { played = 0; covered = 0; }
        var row = JSON.createObjectNode();
        row.put("event_id", id.toLowerCase(java.util.Locale.ROOT));
        row.put("session_key", sessionKey(text(event, "playbackSessionId")));
        row.put("sequence", number(event, "sequence"));
        row.put("occurred_at", TIME.format(time));
        row.put("content_id", positive(event, "contentId"));
        row.put("video_id", positive(event, "videoId"));
        row.put("viewer_id", "user:" + positive(event, "userId"));
        row.put("duration_ms", duration);
        row.put("watch_ms", played);
        row.put("interval_start_ms", end - covered);
        row.put("interval_end_ms", end);
        row.put("is_start", type.equals("PLAYBACK_SESSION_STARTED") ? 1 : 0);
        row.put("episode_id", optionalId(context, "episodeId"));
        row.put("next_episode_id", optionalId(context, "nextEpisodeId"));
        row.put("catalog_enriched", context.isObject() ? 1 : 0);
        return JSON.writeValueAsString(row);
    }

    private static String sessionKey(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 256)
            throw new IllegalArgumentException("Invalid " + field);
        return value.textValue();
    }
    private static long number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0
                || value.longValue() > 9_007_199_254_740_991L) throw new IllegalArgumentException("Invalid " + field);
        return value.longValue();
    }
    private static long positive(JsonNode node, String field) {
        long value = number(node, field);
        if (value == 0) throw new IllegalArgumentException("Invalid " + field);
        return value;
    }
    private static long optionalId(JsonNode node, String field) {
        return node.path(field).isMissingNode() || node.path(field).isNull() ? 0 : positive(node, field);
    }
}
