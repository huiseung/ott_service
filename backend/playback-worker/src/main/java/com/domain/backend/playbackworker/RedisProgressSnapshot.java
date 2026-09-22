package com.domain.backend.playbackworker;

import com.domain.backend.playback.domain.WatchEventType;
import java.time.Instant;
import java.util.Map;

public record RedisProgressSnapshot(
        Long userId,
        Long videoId,
        Long mediaPackageId,
        String playbackSessionId,
        Long playbackSessionDbId,
        long positionSeconds,
        long durationSeconds,
        long sequence,
        boolean completed,
        WatchEventType lastEventType,
        Instant updatedAt
) {

    static RedisProgressSnapshot from(String member, Map<Object, Object> values) {
        if (values.isEmpty()) {
            return null;
        }
        String[] parts = member.split(":");
        if (parts.length != 2) {
            return null;
        }
        return new RedisProgressSnapshot(
                Long.parseLong(parts[0]),
                Long.parseLong(parts[1]),
                Long.parseLong(required(values, "mediaPackageId")),
                required(values, "playbackSessionId"),
                Long.parseLong(required(values, "playbackSessionDbId")),
                Long.parseLong(required(values, "positionSeconds")),
                Long.parseLong(required(values, "durationSeconds")),
                Long.parseLong(required(values, "sequence")),
                Boolean.parseBoolean(required(values, "completed")),
                WatchEventType.valueOf(required(values, "eventType")),
                Instant.ofEpochMilli(Long.parseLong(required(values, "updatedAtEpochMs")))
        );
    }

    private static String required(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing Redis progress field: " + key);
        }
        return value.toString();
    }
}
