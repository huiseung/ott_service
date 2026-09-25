package com.domain.backend.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SubscriptionOutbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    public SubscriptionOutbox(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String type, String subscriptionId, String checkoutId, long userId,
                       Long contentId, String ctaEventId, boolean firstActivation, Instant now) {
        String eventId = UUID.randomUUID().toString();
        var event = json.createObjectNode();
        event.put("eventId", eventId).put("eventType", type).put("eventVersion", 1);
        event.put("occurredAt", now.toString()).put("receivedAt", now.toString());
        event.put("userId", userId).put("producer", "user-api").put("platform", "SERVER");
        event.put("sessionId", "subscription:" + subscriptionId);
        if (contentId != null) event.put("contentId", contentId);
        var payload = event.putObject("payload");
        payload.put("subscriptionId", subscriptionId).put("checkoutId", checkoutId);
        payload.put("ctaEventId", ctaEventId).put("firstActivation", firstActivation);
        payload.put("activationSource", "LOCAL_TEST");
        try {
            jdbc.update("""
                    insert into subscription_outbox(event_id,aggregate_id,event_type,payload,available_at,created_at)
                    values (?,?,?,?,?,?)
                    """, eventId, subscriptionId, type, json.writeValueAsString(event), Timestamp.from(now), Timestamp.from(now));
        } catch (JsonProcessingException error) { throw new IllegalStateException("Cannot serialize subscription event", error); }
    }
}
