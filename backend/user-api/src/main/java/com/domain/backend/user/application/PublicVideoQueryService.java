package com.domain.backend.user.application;

import com.domain.backend.user.application.PublicVideoDtos.VideoDetailResponse;
import com.domain.backend.user.application.PublicVideoDtos.VideoListItem;
import com.domain.backend.user.application.PublicVideoDtos.VideoListResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PublicVideoQueryService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final ContentEligibility eligibility;
    private final JdbcTemplate jdbcTemplate;

    public PublicVideoQueryService(JdbcTemplate jdbcTemplate, ContentEligibility eligibility) {
        this.eligibility = eligibility;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public VideoListResponse listVideos(Long cursor, Integer size) {
        int limit = Math.max(1, Math.min(size == null ? DEFAULT_SIZE : size, MAX_SIZE));
        Long effectiveCursor = cursor == null ? Long.MAX_VALUE : cursor;
        Object[] window = eligibility.parameters();
        List<VideoListItem> rows = jdbcTemplate.query("""
                        select v.id, v.title
                        from videos v
                        join media_packages p on p.id = v.published_media_package_id
                        """ + ContentEligibility.JOINS + " where " + ContentEligibility.PREDICATE + """
                          and v.status = 'READY'
                          and p.status = 'READY'
                          and v.published_media_package_id is not null
                          and v.id < ?
                        order by v.id desc
                        limit ?
                        """,
                (rs, rowNum) -> new VideoListItem(rs.getLong("id"), rs.getString("title")),
                window[0], window[1], window[2], window[3],
                effectiveCursor,
                limit + 1);
        boolean hasNext = rows.size() > limit;
        List<VideoListItem> items = hasNext ? rows.subList(0, limit) : rows;
        Long nextCursor = hasNext && !items.isEmpty() ? items.getLast().videoId() : null;
        return new VideoListResponse(items, nextCursor, hasNext);
    }

    @Transactional(readOnly = true)
    public VideoDetailResponse getVideo(Long videoId) {
        eligibility.requirePlayable(videoId);
        return jdbcTemplate.query("""
                        select v.id, v.title, p.duration_ms
                        from videos v
                        join media_packages p on p.id = v.published_media_package_id
                        where v.id = ?
                          and v.status = 'READY'
                          and p.status = 'READY'
                        """,
                rs -> {
                    if (!rs.next()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found");
                    }
                    return new VideoDetailResponse(rs.getLong("id"), rs.getString("title"), rs.getLong("duration_ms"));
                },
                videoId);
    }
}
