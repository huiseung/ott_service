package com.domain.backend.user.application;

import com.domain.backend.user.application.UserWatchHistoryDtos.WatchHistoryItem;
import com.domain.backend.user.application.UserWatchHistoryDtos.WatchHistoryResponse;
import com.domain.backend.user.infrastructure.persistence.WatchProgressRepository;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserWatchHistoryService {

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final WatchProgressRepository progressRepository;

    public UserWatchHistoryService(JdbcTemplate jdbcTemplate, WatchProgressRepository progressRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.progressRepository = progressRepository;
    }

    @Transactional(readOnly = true)
    public WatchHistoryResponse list(UserPrincipal principal, Integer size) {
        int limit = Math.max(1, Math.min(size == null ? DEFAULT_SIZE : size, MAX_SIZE));
        List<WatchHistoryItem> items = jdbcTemplate.query("""
                        select v.id, v.title, wp.position_ms, wp.duration_ms, wp.updated_at
                        from watch_progress wp
                        join videos v on v.id = wp.video_id
                        where wp.user_id = ?
                        order by wp.updated_at desc
                        limit ?
                        """,
                (rs, rowNum) -> {
                    long positionMs = rs.getLong("position_ms");
                    long durationMs = rs.getLong("duration_ms");
                    double progressRate = durationMs == 0 ? 0 : (double) positionMs / durationMs;
                    return new WatchHistoryItem(
                            rs.getLong("id"),
                            rs.getString("title"),
                            positionMs,
                            durationMs,
                            progressRate,
                            rs.getTimestamp("updated_at").toInstant()
                    );
                },
                principal.userId(),
                limit);
        return new WatchHistoryResponse(items);
    }

    @Transactional
    public void delete(UserPrincipal principal, Long videoId) {
        progressRepository.deleteByIdUserIdAndIdVideoId(principal.userId(), videoId);
    }
}
