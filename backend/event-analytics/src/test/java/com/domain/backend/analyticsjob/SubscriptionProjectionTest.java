package com.domain.backend.analyticsjob;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SubscriptionProjectionTest {
    private static final String EVENT = """
      {"eventId":"00000000-0000-0000-0000-000000000001","eventVersion":1,"eventType":"SUBSCRIPTION_ACTIVATED",
       "occurredAt":"2026-09-23T00:00:00Z","producer":"user-api","platform":"SERVER","userId":42,
       "payload":{"subscriptionId":"00000000-0000-0000-0000-000000000002",
       "checkoutId":"00000000-0000-0000-0000-000000000003","firstActivation":true,"activationSource":"LOCAL_TEST"}}
      """;
    @Test void preservesActivationIdentityWithoutInventingContentAttribution() throws Exception {
        String row = SubscriptionProjection.project(EVENT, false);
        assertTrue(row.contains("\"content_id\":0"));
        assertTrue(row.contains("\"first_activation\":1"));
        assertTrue(row.contains("LOCAL_TEST"));
    }
    @Test void refusesClientSubscriptionFactsAndMalformedIdentity() {
        assertThrows(IllegalArgumentException.class, () -> SubscriptionProjection.project(EVENT.replace("user-api", "user-web"), false));
        assertThrows(IllegalArgumentException.class, () -> SubscriptionProjection.project(EVENT.replace("00000000-0000-0000-0000-000000000003", "bad"), false));
    }
    @Test void ignoresOtherVersionsAndEvents() throws Exception {
        assertNull(SubscriptionProjection.project(EVENT.replace("\"eventVersion\":1", "\"eventVersion\":99"), false));
        assertNull(SubscriptionProjection.project(EVENT.replace("SUBSCRIPTION_ACTIVATED", "PAYMENT_SUCCEEDED"), false));
    }
}
