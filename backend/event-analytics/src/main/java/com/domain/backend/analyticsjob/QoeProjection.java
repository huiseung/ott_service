package com.domain.backend.analyticsjob;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;

/** Quality facts from the corrected player contract, without tokens, URLs or error messages. */
public final class QoeProjection {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long MAX_DURATION_MS = 86_400_000;

    public static String project(String raw) throws JsonProcessingException {
        JsonNode event = JSON.readTree(raw);
        if (event == null || !event.isObject()) throw new IllegalArgumentException("Invalid envelope");
        JsonNode facts = event.path("payload");
        if (!facts.path("qoeVersion").isIntegralNumber() || facts.path("qoeVersion").asLong() != 1) return null;
        String observation = PlaybackObservationProjection.project(raw);
        if (observation == null) return null;
        JsonNode normalized = JSON.readTree(observation);
        ObjectNode row = JSON.createObjectNode();
        for (String field : new String[]{"event_id", "session_key", "sequence", "occurred_at", "content_id", "video_id", "watch_ms"})
            row.set(field, normalized.get(field));
        if (normalized.path("sequence").asLong() == 0) throw new IllegalArgumentException("Invalid sequence");
        String type = event.path("eventType").asText();
        row.put("event_type", type);
        if (type.equals("PLAY") && facts.has("startupTimeMs")) row.put("startup_ms", duration(facts, "startupTimeMs"));
        else row.putNull("startup_ms");
        row.put("buffer_ms", type.equals("BUFFER_ENDED") ? duration(facts, "bufferingDurationMs") : 0);
        boolean error = type.equals("PLAYBACK_ERROR");
        if (error && !facts.path("fatal").isBoolean()) throw new IllegalArgumentException("Missing fatal flag");
        row.put("fatal", error && facts.path("fatal").booleanValue() ? 1 : 0);
        row.put("error_source", error ? source(facts) : "");
        row.put("error_code", error ? code(facts) : "");
        return JSON.writeValueAsString(row);
    }

    private static long duration(JsonNode facts, String field) {
        JsonNode value = facts.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0 || value.longValue() > MAX_DURATION_MS)
            throw new IllegalArgumentException("Invalid " + field);
        return value.longValue();
    }
    private static String source(JsonNode facts) {
        String value = facts.path("source").asText();
        if (!Set.of("hls", "media").contains(value)) throw new IllegalArgumentException("Invalid error source");
        return value;
    }
    private static String code(JsonNode facts) {
        JsonNode value = facts.path("code");
        if (!value.isTextual() || !value.asText().matches("[A-Za-z0-9_]{1,64}"))
            throw new IllegalArgumentException("Invalid error code");
        return value.asText();
    }
}
