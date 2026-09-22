package com.domain.backend.playbackworker;

import com.domain.backend.playback.domain.WatchEventType;
import com.domain.backend.playback.event.WatchEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisProgressStore {

    static final String DIRTY_KEY = "watch-progress:dirty";
    static final String FLUSHING_KEY = "watch-progress:flushing";

    private static final DefaultRedisScript<Long> UPDATE_SCRIPT = new DefaultRedisScript<>("""
            local currentSession = redis.call('HGET', KEYS[1], 'playbackSessionId')
            local currentSeq = tonumber(redis.call('HGET', KEYS[1], 'sequence') or '-1')
            local currentUpdated = tonumber(redis.call('HGET', KEYS[1], 'updatedAtEpochMs') or '-1')
            local incomingSeq = tonumber(ARGV[7])
            local incomingUpdated = tonumber(ARGV[10])
            local accept = 0
            if not currentSession then
              accept = 1
            elseif currentSession == ARGV[4] and incomingSeq >= currentSeq then
              accept = 1
            elseif currentSession ~= ARGV[4] and incomingUpdated >= currentUpdated then
              accept = 1
            end
            if accept == 1 then
              redis.call('HSET', KEYS[1],
                'userId', ARGV[1],
                'videoId', ARGV[2],
                'mediaPackageId', ARGV[3],
                'playbackSessionId', ARGV[4],
                'playbackSessionDbId', ARGV[5],
                'positionSeconds', ARGV[6],
                'sequence', ARGV[7],
                'durationSeconds', ARGV[8],
                'eventType', ARGV[9],
                'updatedAtEpochMs', ARGV[10],
                'completed', ARGV[11])
              redis.call('EXPIRE', KEYS[1], tonumber(ARGV[14]))
              redis.call('ZADD', KEYS[2], tonumber(ARGV[12]), ARGV[13])
            end
            return accept
            """, Long.class);

    private static final DefaultRedisScript<List> CLAIM_SCRIPT = new DefaultRedisScript<>("""
            local members = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[3])
            for i, member in ipairs(members) do
              if redis.call('ZREM', KEYS[1], member) == 1 then
                redis.call('ZADD', KEYS[2], ARGV[2], member)
              end
            end
            return members
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final PlaybackWorkerProperties properties;

    public RedisProgressStore(StringRedisTemplate redisTemplate, PlaybackWorkerProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public boolean apply(WatchEvent event) {
        long now = event.occurredAt().toEpochMilli();
        String member = member(event.userId(), event.videoId());
        long dueAt = flushDueAt(event, now);
        Long accepted = redisTemplate.execute(
                UPDATE_SCRIPT,
                List.of(progressKey(event.userId(), event.videoId()), DIRTY_KEY),
                event.userId().toString(),
                event.videoId().toString(),
                event.mediaPackageId().toString(),
                event.playbackSessionId(),
                event.playbackSessionDbId().toString(),
                Long.toString(event.positionSeconds()),
                Long.toString(event.sequence()),
                Long.toString(event.durationSeconds()),
                event.eventType().name(),
                Long.toString(now),
                Boolean.toString(event.eventType() == WatchEventType.COMPLETE),
                Long.toString(dueAt),
                member,
                Long.toString(properties.getProgressTtl().toSeconds())
        );
        return accepted != null && accepted == 1L;
    }

    public List<String> claimDue(int limit, Instant now) {
        List result = redisTemplate.execute(
                CLAIM_SCRIPT,
                List.of(DIRTY_KEY, FLUSHING_KEY),
                Long.toString(now.toEpochMilli()),
                Long.toString(now.plus(properties.getFlushClaimTtl()).toEpochMilli()),
                Integer.toString(limit)
        );
        List<String> members = new ArrayList<>();
        if (result != null) {
            for (Object member : result) {
                members.add(member.toString());
            }
        }
        return members;
    }

    public RedisProgressSnapshot getSnapshot(String member) {
        String[] parts = member.split(":");
        if (parts.length != 2) {
            return null;
        }
        return RedisProgressSnapshot.from(member, redisTemplate.opsForHash().entries(progressKey(parts[0], parts[1])));
    }

    public void completeFlush(List<String> members) {
        if (!members.isEmpty()) {
            redisTemplate.opsForZSet().remove(FLUSHING_KEY, members.toArray());
        }
    }

    public void requeue(List<String> members, Instant dueAt) {
        for (String member : members) {
            redisTemplate.opsForZSet().remove(FLUSHING_KEY, member);
            redisTemplate.opsForZSet().add(DIRTY_KEY, member, dueAt.toEpochMilli());
        }
    }

    public void recoverExpiredClaims(Instant now) {
        var expired = redisTemplate.opsForZSet().rangeByScore(FLUSHING_KEY, Double.NEGATIVE_INFINITY, now.toEpochMilli(), 0, properties.getFlushBatchSize());
        if (expired == null || expired.isEmpty()) {
            return;
        }
        for (String member : expired) {
            redisTemplate.opsForZSet().remove(FLUSHING_KEY, member);
            redisTemplate.opsForZSet().add(DIRTY_KEY, member, now.toEpochMilli());
        }
    }

    private long flushDueAt(WatchEvent event, long now) {
        if (event.eventType() == WatchEventType.PAUSE
                || event.eventType() == WatchEventType.COMPLETE
                || event.eventType() == WatchEventType.SESSION_END) {
            return now;
        }
        return now + properties.getProgressCheckpointInterval().toMillis();
    }

    private String progressKey(Long userId, Long videoId) {
        return "watch-progress:%d:%d".formatted(userId, videoId);
    }

    private String progressKey(String userId, String videoId) {
        return "watch-progress:%s:%s".formatted(userId, videoId);
    }

    private String member(Long userId, Long videoId) {
        return "%d:%d".formatted(userId, videoId);
    }
}
