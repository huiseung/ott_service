package com.domain.backend.user.infrastructure.persistence;

import com.domain.backend.user.domain.WatchProgress;
import com.domain.backend.user.domain.WatchProgressId;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface WatchProgressRepository extends JpaRepository<WatchProgress, WatchProgressId> {

    Optional<WatchProgress> findByIdUserIdAndIdVideoId(Long userId, Long videoId);

    void deleteByIdUserIdAndIdVideoId(Long userId, Long videoId);

    @Modifying
    @Transactional
    @Query(value = """
            insert into watch_progress(user_id, video_id, media_package_id, playback_session_id, position_ms,
                                       duration_ms, client_event_seq, occurred_at, updated_at)
            values (:userId, :videoId, :mediaPackageId, :playbackSessionId, :positionMs,
                    :durationMs, :clientEventSeq, :occurredAt, :updatedAt)
            on duplicate key update
                media_package_id = if(
                    (playback_session_id = values(playback_session_id) and values(client_event_seq) >= client_event_seq)
                    or (playback_session_id <> values(playback_session_id) and values(occurred_at) >= occurred_at),
                    values(media_package_id),
                    media_package_id
                ),
                position_ms = if(
                    (playback_session_id = values(playback_session_id) and values(client_event_seq) >= client_event_seq)
                    or (playback_session_id <> values(playback_session_id) and values(occurred_at) >= occurred_at),
                    values(position_ms),
                    position_ms
                ),
                duration_ms = if(
                    (playback_session_id = values(playback_session_id) and values(client_event_seq) >= client_event_seq)
                    or (playback_session_id <> values(playback_session_id) and values(occurred_at) >= occurred_at),
                    values(duration_ms),
                    duration_ms
                ),
                client_event_seq = if(
                    (playback_session_id = values(playback_session_id) and values(client_event_seq) >= client_event_seq)
                    or (playback_session_id <> values(playback_session_id) and values(occurred_at) >= occurred_at),
                    values(client_event_seq),
                    client_event_seq
                ),
                occurred_at = if(
                    (playback_session_id = values(playback_session_id) and values(client_event_seq) >= client_event_seq)
                    or (playback_session_id <> values(playback_session_id) and values(occurred_at) >= occurred_at),
                    values(occurred_at),
                    occurred_at
                ),
                updated_at = if(
                    (playback_session_id = values(playback_session_id) and values(client_event_seq) >= client_event_seq)
                    or (playback_session_id <> values(playback_session_id) and values(occurred_at) >= occurred_at),
                    values(updated_at),
                    updated_at
                ),
                playback_session_id = if(
                    (playback_session_id = values(playback_session_id) and values(client_event_seq) >= client_event_seq)
                    or (playback_session_id <> values(playback_session_id) and values(occurred_at) >= occurred_at),
                    values(playback_session_id),
                    playback_session_id
                )
            """, nativeQuery = true)
    void upsertProgress(Long userId, Long videoId, Long mediaPackageId, Long playbackSessionId, long positionMs,
                        long durationMs, long clientEventSeq, Instant occurredAt, Instant updatedAt);
}
