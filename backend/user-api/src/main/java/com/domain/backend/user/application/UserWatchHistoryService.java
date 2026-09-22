package com.domain.backend.user.application;

import com.domain.backend.user.application.UserWatchHistoryDtos.WatchHistoryItem;
import com.domain.backend.user.application.UserWatchHistoryDtos.WatchHistoryResponse;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserWatchHistoryService {

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;

    public UserWatchHistoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public WatchHistoryResponse list(UserPrincipal principal, Integer size) {
        int limit = Math.max(1, Math.min(size == null ? DEFAULT_SIZE : size, MAX_SIZE));
        List<WatchHistoryItem> items = jdbcTemplate.query("""
                        select v.id, v.title, wh.position_seconds, wh.duration_seconds, wh.updated_at
                        from watch_history wh
                        join videos v on v.id = wh.video_id
                        where wh.user_id = ?
                        order by wh.updated_at desc
                        limit ?
                        """,
                (rs, rowNum) -> {
                    long positionSeconds = rs.getLong("position_seconds");
                    long durationSeconds = rs.getLong("duration_seconds");
                    double progressRate = durationSeconds == 0 ? 0 : (double) positionSeconds / durationSeconds;
                    return new WatchHistoryItem(
                            rs.getLong("id"),
                            rs.getString("title"),
                            positionSeconds,
                            durationSeconds,
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
        jdbcTemplate.update("delete from watch_history where user_id = ? and video_id = ?", principal.userId(), videoId);
    }
}
