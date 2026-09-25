package com.domain.backend.user.application;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicContentQueryService {
    private final JdbcTemplate jdbc;
    private final ContentEligibility eligibility;

    public PublicContentQueryService(JdbcTemplate jdbc, ContentEligibility eligibility) {
        this.jdbc = jdbc;
        this.eligibility = eligibility;
    }

    public record ContentItem(Long contentId, String type, String title, Long videoId) {}
    public record ContentPage(List<ContentItem> items, Long nextCursor, boolean hasNext) {}

    @Transactional(readOnly = true)
    public ContentPage list(Long cursor, Integer size) {
        int limit = Math.max(1, Math.min(size == null ? 20 : size, 50));
        Object[] window = eligibility.parameters();
        var rows = jdbc.query("""
                select content_id, content_type, title, video_id from (
                  select ct.id as content_id, ct.type as content_type, v.id as video_id,
                    coalesce((select cl.title from content_localizations cl
                              where cl.content_id = ct.id order by cl.locale limit 1), v.title) as title,
                    row_number() over (partition by ct.id order by s.season_number, e.episode_number,
                      case when mv.version_type = 'ORIGINAL' then 0 else 1 end, mv.id) as version_rank
                  from videos v join media_packages p on p.id = v.published_media_package_id
                """ + ContentEligibility.JOINS + " where " + ContentEligibility.PREDICATE + """
                  and v.status = 'READY' and p.status = 'READY' and ct.id < ?
                ) available where version_rank = 1 order by content_id desc limit ?
                """, (rs, index) -> new ContentItem(rs.getLong("content_id"), rs.getString("content_type"),
                rs.getString("title"), rs.getLong("video_id")),
                window[0], window[1], window[2], window[3], cursor == null ? Long.MAX_VALUE : cursor, limit + 1);
        boolean hasNext = rows.size() > limit;
        var items = hasNext ? rows.subList(0, limit) : rows;
        return new ContentPage(items, hasNext ? items.getLast().contentId() : null, hasNext);
    }
}
