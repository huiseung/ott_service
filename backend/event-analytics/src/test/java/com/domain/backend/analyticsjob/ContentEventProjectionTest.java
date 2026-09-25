package com.domain.backend.analyticsjob;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContentEventProjectionTest {
    private static final String START = """
        {"eventId":"00000000-0000-0000-0000-000000000001","eventVersion":1,
         "eventType":"PLAYBACK_SESSION_STARTED","occurredAt":"2026-09-23T00:30:00+09:00",
         "userId":42,"anonymousId":"device","contentId":10,
         "playbackSessionId":"must-not-be-stored","payload":{"future":"kept-only-in-raw"}}
        """;

    @Test void projectsUtcAndAuthenticatedIdentityWithoutRawOrCredentials() throws Exception {
        var row = new ObjectMapper().readTree(ContentEventProjection.project(START, "playback"));
        assertEquals("2026-09-22 15:30:00.000", row.path("occurred_at").asText());
        assertEquals("user:42", row.path("viewer_id").asText());
        assertEquals(6, row.size());
        assertFalse(row.toString().contains("must-not-be-stored"));
    }

    @Test void retryProjectionDoesNotDependOnReceivedTime() throws Exception {
        assertEquals(ContentEventProjection.project(START, "playback"),
                ContentEventProjection.project(START.replace("\"userId\":42", "\"receivedAt\":\"later\",\"userId\":42"), "playback"));
    }

    @Test void ignoresFutureVersionAndEventsOutsideServingScope() throws Exception {
        assertNull(ContentEventProjection.project(START.replace("\"eventVersion\":1", "\"eventVersion\":99"), "playback"));
        assertNull(ContentEventProjection.project(START.replace("PLAYBACK_SESSION_STARTED", "HEARTBEAT"), "playback"));
        assertNull(ContentEventProjection.project(START, "behavior"));
    }

    @Test void rejectsMalformedSupportedFacts() {
        assertThrows(Exception.class, () -> ContentEventProjection.project("not-json", "playback"));
        assertThrows(IllegalArgumentException.class, () -> ContentEventProjection.project(START.replace("\"contentId\":10", "\"contentId\":0"), "playback"));
        assertThrows(IllegalArgumentException.class, () -> ContentEventProjection.project(START.replace("\"userId\":42", "\"userId\":1.5"), "playback"));
        assertThrows(IllegalArgumentException.class, () -> ContentEventProjection.project(START.replace("00000000-0000-0000-0000-000000000001", "1-1-1-1-1"), "playback"));
    }

    @Test void supportsAnonymousContentBehavior() throws Exception {
        String behavior = START.replace("PLAYBACK_SESSION_STARTED", "CONTENT_CLICK").replace("\"userId\":42", "\"userId\":null");
        assertTrue(ContentEventProjection.project(behavior, "behavior").contains("anonymous:device"));
    }
}
