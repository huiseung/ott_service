package com.domain.backend.analyticsjob;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QoeProjectionTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ObjectNode event(String type, String extra) throws Exception {
        return (ObjectNode) JSON.readTree("""
            {"eventId":"00000000-0000-0000-0000-000000000001","eventVersion":1,
             "eventType":"%s","occurredAt":"2026-09-24T00:00:00Z","contentId":10,"videoId":20,
             "userId":42,"playbackSessionId":"secret-session-token","sequence":2,
             "payload":{"qoeVersion":1,"positionMs":1000,"previousPositionMs":0,
                        "playedMsSincePreviousEvent":1000,"durationMs":60000,"playbackRate":1%s}}
            """.formatted(type, extra));
    }
    @Test void extractsStartupBufferAndWallTimeWithoutRawIdentity() throws Exception {
        var row = JSON.readTree(QoeProjection.project(event("PLAY", ",\"startupTimeMs\":1200").toString()));
        assertEquals(1200, row.path("startup_ms").asLong());
        assertEquals(1000, row.path("watch_ms").asLong());
        assertEquals(64, row.path("session_key").asText().length());
        assertFalse(row.toString().contains("secret-session-token"));
        assertFalse(row.has("viewer_id"));
        row = JSON.readTree(QoeProjection.project(event("BUFFER_ENDED", ",\"bufferingDurationMs\":2000").toString()));
        assertEquals(2000, row.path("buffer_ms").asLong());
        assertTrue(row.path("startup_ms").isNull());
    }
    @Test void onlyVersionedCorrectedPlayerEventsEnterQoe() throws Exception {
        var legacy = event("PLAY", ",\"startupTimeMs\":100");
        ((ObjectNode) legacy.path("payload")).remove("qoeVersion");
        assertNull(QoeProjection.project(legacy.toString()));
        legacy.put("eventVersion", 2);
        assertNull(QoeProjection.project(legacy.toString()));
        var unknown = event("UNKNOWN", "");
        assertNull(QoeProjection.project(unknown.toString()));
    }
    @Test void validatesDurationsAndErrorFacts() throws Exception {
        for (String invalid : new String[]{"-1", "0.5", "86400001", "\"100\"", "null"}) {
            var raw = event("BUFFER_ENDED", ",\"bufferingDurationMs\":" + invalid).toString();
            assertThrows(IllegalArgumentException.class, () -> QoeProjection.project(raw));
        }
        var missing = event("BUFFER_ENDED", "").toString();
        assertThrows(IllegalArgumentException.class, () -> QoeProjection.project(missing));
        var error = event("PLAYBACK_ERROR", ",\"source\":\"hls\",\"code\":\"networkError\",\"fatal\":true");
        var row = JSON.readTree(QoeProjection.project(error.toString()));
        assertEquals(1, row.path("fatal").asInt());
        assertEquals("networkError", row.path("error_code").asText());
        ((ObjectNode) error.path("payload")).put("code", "https://private/path?token=secret");
        assertThrows(IllegalArgumentException.class, () -> QoeProjection.project(error.toString()));
    }
    @Test void noStartupSampleIsDifferentFromZeroLatency() throws Exception {
        var absent = JSON.readTree(QoeProjection.project(event("PLAY", "").toString()));
        var zero = JSON.readTree(QoeProjection.project(event("PLAY", ",\"startupTimeMs\":0").toString()));
        assertTrue(absent.path("startup_ms").isNull());
        assertEquals(0, zero.path("startup_ms").asLong());
    }
}
