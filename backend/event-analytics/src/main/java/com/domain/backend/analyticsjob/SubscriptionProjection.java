package com.domain.backend.analyticsjob;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

public final class SubscriptionProjection {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
    private static final Set<String> TYPES = Set.of("SUBSCRIPTION_CREATED", "CHECKOUT_STARTED", "SUBSCRIPTION_ACTIVATED", "SUBSCRIPTION_CANCELLED");

    public static String project(String raw, boolean cta) throws JsonProcessingException {
        JsonNode event = JSON.readTree(raw);
        if (event == null || !event.isObject()) throw new IllegalArgumentException("Invalid envelope");
        if (!event.path("eventVersion").isIntegralNumber() || !event.path("eventVersion").canConvertToInt()
                || event.path("eventVersion").intValue() != 1) return null;
        String type = text(event, "eventType");
        if (cta ? !type.equals("SUBSCRIPTION_CTA_CLICK") : !TYPES.contains(type)) return null;
        if (!cta && (!event.path("producer").asText().equals("user-api") || !event.path("platform").asText().equals("SERVER")))
            throw new IllegalArgumentException("Subscription facts must come from backend");
        var row = JSON.createObjectNode();
        row.put("event_id", uuid(event, "eventId", false));
        Instant occurred = Instant.parse(text(event, "occurredAt"));
        if (occurred.isBefore(Instant.EPOCH) || !occurred.isBefore(Instant.parse("2300-01-01T00:00:00Z")))
            throw new IllegalArgumentException("Invalid timestamp");
        row.put("occurred_at", TIME.format(occurred));
        row.put("viewer_id", "user:" + positive(event, "userId"));
        if (cta) row.put("content_id", positive(event, "contentId"));
        else {
            var payload = event.path("payload");
            row.put("event_type", type);
            row.put("subscription_id", uuid(payload, "subscriptionId", false));
            row.put("checkout_id", uuid(payload, "checkoutId", !(type.equals("CHECKOUT_STARTED") || type.equals("SUBSCRIPTION_ACTIVATED"))));
            row.put("cta_event_id", uuid(payload, "ctaEventId", true));
            row.put("content_id", event.path("contentId").isMissingNode() || event.path("contentId").isNull() ? 0 : positive(event, "contentId"));
            if (!payload.path("firstActivation").isBoolean()) throw new IllegalArgumentException("Invalid activation flag");
            row.put("first_activation", payload.path("firstActivation").booleanValue() ? 1 : 0);
            String source = text(payload, "activationSource");
            if (!source.equals("LOCAL_TEST")) throw new IllegalArgumentException("Unsupported activation source");
            row.put("activation_source", source);
        }
        return JSON.writeValueAsString(row);
    }
    private static String text(JsonNode node, String field) {
        if (!node.path(field).isTextual() || node.path(field).textValue().isBlank()) throw new IllegalArgumentException("Invalid " + field);
        return node.path(field).textValue();
    }
    private static long positive(JsonNode node, String field) {
        var value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) throw new IllegalArgumentException("Invalid " + field);
        return value.longValue();
    }
    private static String uuid(JsonNode node, String field, boolean optional) {
        if (optional && (node.path(field).isNull() || node.path(field).isMissingNode())) return "";
        String text = text(node, field);
        String canonical = UUID.fromString(text).toString();
        if (!canonical.equalsIgnoreCase(text)) throw new IllegalArgumentException("Invalid UUID");
        return canonical;
    }
}
