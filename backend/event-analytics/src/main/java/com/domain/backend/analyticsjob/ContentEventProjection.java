package com.domain.backend.analyticsjob;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/** Only the small set of facts used by stage 4 serving queries; raw stays in MinIO. */
public final class ContentEventProjection {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneOffset.UTC);
    private static final Set<String> BEHAVIOR = Set.of("CONTENT_IMPRESSION", "CONTENT_CLICK",
            "CONTENT_DETAIL_VIEW", "SUBSCRIPTION_CTA_CLICK");

    /** null = intentionally outside scope/version; malformed supported events throw. */
    public static String project(String raw, String domain) throws JsonProcessingException {
        JsonNode event = JSON.readTree(raw);
        if (event == null || !event.isObject()) throw new IllegalArgumentException("Invalid envelope");
        if (!event.path("eventVersion").isIntegralNumber() || !event.path("eventVersion").canConvertToInt()
                || event.path("eventVersion").intValue() != 1)
            return null;
        String type = text(event, "eventType");
        if (!(domain.equals("playback") && type.equals("PLAYBACK_SESSION_STARTED"))
                && !(domain.equals("behavior") && BEHAVIOR.contains(type))) return null;
        String id = text(event, "eventId");
        UUID uuid = UUID.fromString(id);
        if (!uuid.toString().equalsIgnoreCase(id)) throw new IllegalArgumentException("Invalid UUID");
        Instant occurred = Instant.parse(text(event, "occurredAt"));
        if (occurred.isBefore(Instant.EPOCH) || !occurred.isBefore(Instant.parse("2300-01-01T00:00:00Z")))
            throw new IllegalArgumentException("Timestamp outside serving range");
        long contentId = positiveId(event, "contentId");
        String viewer = event.path("userId").isNull() || event.path("userId").isMissingNode()
                ? "anonymous:" + text(event, "anonymousId") : "user:" + positiveId(event, "userId");
        var row = JSON.createObjectNode();
        row.put("domain", domain);
        row.put("event_id", uuid.toString());
        row.put("event_type", type);
        row.put("occurred_at", TIME.format(occurred));
        row.put("content_id", contentId);
        row.put("viewer_id", viewer);
        // Playback session tokens are credentials; never copy them into serving tables.
        return JSON.writeValueAsString(row);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 256)
            throw new IllegalArgumentException("Invalid " + field);
        return value.textValue();
    }

    private static long positiveId(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0)
            throw new IllegalArgumentException("Invalid " + field);
        return value.longValue();
    }
}
