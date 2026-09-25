package com.domain.backend.analyticsjob;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackObservationProjectionTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private String event(String type, long previous, long position, long played, double rate) {
        return """
            {"eventId":"00000000-0000-0000-0000-000000000001","eventVersion":1,
             "eventType":"%s","occurredAt":"2026-09-23T00:00:00Z","contentId":10,"videoId":20,
             "userId":42,"playbackSessionId":"secret-session-token","sequence":2,
             "playbackContext":{"durationMs":100000,"episodeId":101,"nextEpisodeId":102},
             "payload":{"positionMs":%d,"previousPositionMs":%d,"playedMsSincePreviousEvent":%d,
                        "durationMs":999999,"playbackRate":%s,"fromPositionMs":10000}}
            """.formatted(type, position, previous, played, rate);
    }
    private JsonNode project(String event) throws Exception { return JSON.readTree(PlaybackObservationProjection.project(event)); }

    @Test void playbackRateSeparatesWallTimeAndMediaCoverage() throws Exception {
        var row = project(event("HEARTBEAT", 0, 20000, 10000, 2));
        assertEquals(10000, row.path("watch_ms").asLong());
        assertEquals(20000, row.path("interval_end_ms").asLong());
        assertEquals(0, row.path("interval_start_ms").asLong());
        assertEquals(100000, row.path("duration_ms").asLong());
        assertEquals(101, row.path("episode_id").asLong());
        assertEquals(64, row.path("session_key").asText().length());
        assertFalse(row.toString().contains("secret-session-token"));
    }
    @Test void seekOnlyCoversBeforeSeekAndEndedFlagCannotComplete() throws Exception {
        var row = project(event("SEEK", 0, 90000, 10000, 1));
        assertEquals(10000, row.path("interval_end_ms").asLong());
        assertEquals(0, row.path("interval_start_ms").asLong());
        var ended = project(event("PLAYBACK_ENDED", 90000, 100000, 0, 1));
        assertEquals(ended.path("interval_start_ms"), ended.path("interval_end_ms"));
    }
    @Test void jumpCoverageIsCappedByObservedAdvancingTime() throws Exception {
        var row = project(event("HEARTBEAT", 0, 90000, 10000, 1));
        assertEquals(80000, row.path("interval_start_ms").asLong());
        assertEquals(90000, row.path("interval_end_ms").asLong());
    }
    @Test void pausedOrRewoundSamplesHaveNoInterval() throws Exception {
        var row = project(event("PAUSE", 20000, 20000, 0, 1));
        assertEquals(row.path("interval_start_ms"), row.path("interval_end_ms"));
        row = project(event("HEARTBEAT", 20000, 10000, 0, 1));
        assertEquals(row.path("interval_start_ms"), row.path("interval_end_ms"));
    }
    @Test void zeroDurationRemainsUnknownAndOldEventsKeepClientDuration() throws Exception {
        var row = project(event("PLAYBACK_SESSION_STARTED", 0, 0, 0, 1).replace("\"durationMs\":100000", "\"durationMs\":0"));
        assertEquals(0, row.path("duration_ms").asLong());
        var old = JSON.readTree(event("HEARTBEAT", 0, 10000, 10000, 1));
        ((com.fasterxml.jackson.databind.node.ObjectNode) old).remove("playbackContext");
        row = project(old.toString());
        assertEquals(999999, row.path("duration_ms").asLong());
        assertEquals(0, row.path("episode_id").asLong());
        assertEquals(0, row.path("catalog_enriched").asInt());
    }
    @Test void malformedDeltasRejectWithoutEmittingMetrics() {
        assertThrows(IllegalArgumentException.class, () -> project(event("HEARTBEAT", 0, 1000, -1, 1)));
        assertThrows(IllegalArgumentException.class, () -> project(event("HEARTBEAT", 0, 1000, 1000, 0)));
    }
}
