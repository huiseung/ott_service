package com.domain.backend.playbackworker;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class WatchHistoryBatchWriter {

    private final JdbcTemplate jdbcTemplate;

    public WatchHistoryBatchWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void upsert(List<RedisProgressSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(upsertSql(),
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                        RedisProgressSnapshot snapshot = snapshots.get(i);
                        Instant now = Instant.now();
                        ps.setLong(1, snapshot.userId());
                        ps.setLong(2, snapshot.videoId());
                        ps.setLong(3, snapshot.mediaPackageId());
                        ps.setString(4, snapshot.playbackSessionId());
                        ps.setLong(5, snapshot.positionSeconds());
                        ps.setLong(6, snapshot.durationSeconds());
                        ps.setLong(7, snapshot.sequence());
                        ps.setBoolean(8, snapshot.completed());
                        ps.setString(9, snapshot.lastEventType().name());
                        ps.setTimestamp(10, Timestamp.from(snapshot.updatedAt()));
                        ps.setTimestamp(11, Timestamp.from(now));
                        ps.setTimestamp(12, Timestamp.from(snapshot.updatedAt()));
                    }

                    @Override
                    public int getBatchSize() {
                        return snapshots.size();
                    }
                });
    }

    static String upsertSql() {
        return """
                        insert into watch_history(user_id, video_id, media_package_id, playback_session_id,
                                                  position_seconds, duration_seconds, sequence, completed,
                                                  last_event_type, last_watched_at, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on duplicate key update
                            media_package_id = if(
                                                  (playback_session_id = values(playback_session_id) and values(sequence) >= sequence)
                                                  or (playback_session_id <> values(playback_session_id) and values(updated_at) >= updated_at),
                                                  values(media_package_id), media_package_id),
                            position_seconds = if(
                                                  (playback_session_id = values(playback_session_id) and values(sequence) >= sequence)
                                                  or (playback_session_id <> values(playback_session_id) and values(updated_at) >= updated_at),
                                                  values(position_seconds), position_seconds),
                            duration_seconds = if(
                                                  (playback_session_id = values(playback_session_id) and values(sequence) >= sequence)
                                                  or (playback_session_id <> values(playback_session_id) and values(updated_at) >= updated_at),
                                                  values(duration_seconds), duration_seconds),
                            sequence = if(
                                          (playback_session_id = values(playback_session_id) and values(sequence) >= sequence)
                                          or (playback_session_id <> values(playback_session_id) and values(updated_at) >= updated_at),
                                          values(sequence), sequence),
                            completed = if(
                                           (playback_session_id = values(playback_session_id) and values(sequence) >= sequence)
                                           or (playback_session_id <> values(playback_session_id) and values(updated_at) >= updated_at),
                                           values(completed), completed),
                            last_event_type = if(
                                                 (playback_session_id = values(playback_session_id) and values(sequence) >= sequence)
                                                 or (playback_session_id <> values(playback_session_id) and values(updated_at) >= updated_at),
                                                 values(last_event_type), last_event_type),
                            last_watched_at = if(
                                                 (playback_session_id = values(playback_session_id) and values(sequence) >= sequence)
                                                 or (playback_session_id <> values(playback_session_id) and values(updated_at) >= updated_at),
                                                 values(last_watched_at), last_watched_at),
                            updated_at = if(
                                            (playback_session_id = values(playback_session_id) and values(sequence) >= sequence)
                                            or (playback_session_id <> values(playback_session_id) and values(updated_at) >= updated_at),
                                            values(updated_at), updated_at),
                            playback_session_id = if(
                                                     (playback_session_id = values(playback_session_id) and values(sequence) >= sequence)
                                                     or (playback_session_id <> values(playback_session_id) and values(updated_at) >= updated_at),
                                                     values(playback_session_id), playback_session_id)
                        """;
    }
}
