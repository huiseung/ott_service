package com.domain.backend.analytics;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsPlaybackLookup {
    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsPlaybackLookup(JdbcTemplate jdbc) { this.jdbc = new NamedParameterJdbcTemplate(jdbc); }

    public record Session(String token, long id, long userId, long videoId, long mediaPackageId,
                          Long contentId, long durationMs, Long episodeId, Integer seasonNumber,
                          Integer episodeNumber, Long nextEpisodeId) {}

    public Map<String, Session> find(Collection<String> tokens, long userId, Instant now) {
        if (tokens.isEmpty()) return Map.of();
        return jdbc.query("""
                select ps.session_token, ps.id, ps.user_id, ps.video_id, ps.media_package_id,
                       coalesce(mv.content_id, s.series_content_id) as content_id, mp.duration_ms,
                       e.id as episode_id, s.season_number, e.episode_number,
                       (select e2.id from episodes e2 join seasons s2 on s2.id = e2.season_id
                        where s2.series_content_id = s.series_content_id
                          and (s2.season_number > s.season_number or
                               (s2.season_number = s.season_number and e2.episode_number > e.episode_number))
                        order by s2.season_number, e2.episode_number limit 1) as next_episode_id
                from playback_sessions ps
                join media_packages mp on mp.id = ps.media_package_id
                left join media_versions mv on mv.video_id = ps.video_id
                left join episodes e on e.id = mv.episode_id
                left join seasons s on s.id = e.season_id
                where ps.session_token in (:tokens) and ps.user_id = :userId and ps.expires_at > :now
                """, Map.of("tokens", tokens, "userId", userId, "now", Timestamp.from(now)),
                (rs, row) -> new Session(rs.getString("session_token"), rs.getLong("id"), rs.getLong("user_id"),
                        rs.getLong("video_id"), rs.getLong("media_package_id"),
                        rs.getObject("content_id", Long.class), rs.getLong("duration_ms"),
                        rs.getObject("episode_id", Long.class), rs.getObject("season_number", Integer.class),
                        rs.getObject("episode_number", Integer.class), rs.getObject("next_episode_id", Long.class)))
                .stream().collect(Collectors.toMap(Session::token, Function.identity()));
    }
}
