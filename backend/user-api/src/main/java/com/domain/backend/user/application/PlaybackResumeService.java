package com.domain.backend.user.application;

import java.time.Duration;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PlaybackResumeService {

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final PlaybackProperties properties;

    public PlaybackResumeService(StringRedisTemplate redisTemplate, JdbcTemplate jdbcTemplate, PlaybackProperties properties) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public ResumeProgress getResumeProgress(Long userId, Long videoId, long durationSeconds) {
        try {
            ResumeProgress cached = fromRedis(userId, videoId, durationSeconds);
            if (cached != null) {
                return cached;
            }
        } catch (RuntimeException ignored) {
        }
        try {
            ResumeProgress persisted = fromMysql(userId, videoId, durationSeconds);
            cache(userId, videoId, persisted);
            return persisted;
        } catch (RuntimeException ignored) {
            return ResumeProgress.empty(durationSeconds);
        }
    }

    private ResumeProgress fromRedis(Long userId, Long videoId, long durationSeconds) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(key(userId, videoId));
        if (values.isEmpty() || values.get("positionSeconds") == null) {
            return null;
        }
        long position = Long.parseLong(values.get("positionSeconds").toString());
        return new ResumeProgress(Math.min(position, durationSeconds), durationSeconds);
    }

    private ResumeProgress fromMysql(Long userId, Long videoId, long durationSeconds) {
        return jdbcTemplate.query("""
                        select position_seconds, duration_seconds
                        from watch_history
                        where user_id = ? and video_id = ?
                        """,
                rs -> {
                    if (!rs.next()) {
                        return ResumeProgress.empty(durationSeconds);
                    }
                    long position = rs.getLong("position_seconds");
                    long storedDuration = rs.getLong("duration_seconds");
                    return new ResumeProgress(Math.min(position, durationSeconds), storedDuration);
                },
                userId,
                videoId);
    }

    private void cache(Long userId, Long videoId, ResumeProgress progress) {
        try {
            String key = key(userId, videoId);
            redisTemplate.opsForHash().putAll(key, Map.of(
                    "positionSeconds", Long.toString(progress.positionSeconds()),
                    "durationSeconds", Long.toString(progress.durationSeconds())
            ));
            redisTemplate.expire(key, properties.getResumeCacheTtl());
        } catch (DataAccessException ignored) {
        }
    }

    private String key(Long userId, Long videoId) {
        return "watch-progress:%d:%d".formatted(userId, videoId);
    }
}
