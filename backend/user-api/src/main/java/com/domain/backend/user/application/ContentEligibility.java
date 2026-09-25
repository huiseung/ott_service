package com.domain.backend.user.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Shared database predicate for discovery and playback. End time is exclusive. */
@Service
public class ContentEligibility {
    public static final String JOINS = """
            join media_versions mv on mv.video_id = v.id
            left join episodes e on e.id = mv.episode_id
            left join seasons s on s.id = e.season_id
            join contents ct on ct.id = coalesce(mv.content_id, s.series_content_id)
            join content_availabilities a on a.content_id = ct.id
            """;
    public static final String PREDICATE = """
            ct.status = 'PUBLISHED' and mv.status = 'PUBLISHED'
            and a.country_code = ? and a.status = 'AVAILABLE'
            and a.available_from <= ? and (a.available_until is null or a.available_until > ?)
            and ((ct.type = 'MOVIE' and mv.content_id = ct.id)
              or (ct.type = 'SERIES' and s.status = 'PUBLISHED' and e.status = 'PUBLISHED'
                  and (e.release_at is null or e.release_at <= ?)))
            """;

    private final JdbcTemplate jdbc;
    private final String country;
    private final Clock clock;

    @Autowired
    public ContentEligibility(JdbcTemplate jdbc, @Value("${app.catalog.country:KR}") String country) {
        this(jdbc, country, Clock.systemUTC());
    }

    ContentEligibility(JdbcTemplate jdbc, String country, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.country = country.trim().toUpperCase(Locale.ROOT);
        if (!this.country.matches("[A-Z]{2}")) throw new IllegalArgumentException("Invalid catalog country");
    }

    public Object[] parameters() {
        Timestamp timestamp = Timestamp.from(clock.instant());
        return new Object[]{country, timestamp, timestamp, timestamp};
    }

    public void requirePlayable(Long videoId) {
        Object[] window = parameters();
        Boolean eligible = jdbc.queryForObject("select exists(select 1 from videos v "
                + "join media_packages p on p.id = v.published_media_package_id " + JOINS
                + "where " + PREDICATE + " and v.status = 'READY' and p.status = 'READY' and v.id = ?)",
                Boolean.class, window[0], window[1], window[2], window[3], videoId);
        if (!Boolean.TRUE.equals(eligible)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content is not available");
        }
    }

    public void requireContentAvailable(Long contentId) {
        Object[] window = parameters();
        Boolean eligible = jdbc.queryForObject("""
                select exists(select 1 from contents ct join content_availabilities a on a.content_id=ct.id
                  where ct.id=? and ct.status='PUBLISHED' and a.country_code=? and a.status='AVAILABLE'
                    and a.available_from<=? and (a.available_until is null or a.available_until>?))
                """, Boolean.class, contentId, window[0], window[1], window[2]);
        if (!Boolean.TRUE.equals(eligible)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content is not available");
    }
}
