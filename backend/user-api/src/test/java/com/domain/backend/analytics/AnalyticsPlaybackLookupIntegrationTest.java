package com.domain.backend.analytics;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class AnalyticsPlaybackLookupIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Test void resolvesNextConfiguredEpisodeAcrossSeasonsAndPreservesSessionAccessChecks() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        jdbc.execute("CREATE TABLE playback_sessions (id BIGINT,session_token VARCHAR(64),user_id BIGINT,video_id BIGINT,media_package_id BIGINT,expires_at DATETIME)");
        jdbc.execute("CREATE TABLE media_packages (id BIGINT,duration_ms BIGINT)");
        jdbc.execute("CREATE TABLE media_versions (video_id BIGINT,content_id BIGINT,episode_id BIGINT)");
        jdbc.execute("CREATE TABLE episodes (id BIGINT,season_id BIGINT,episode_number INT)");
        jdbc.execute("CREATE TABLE seasons (id BIGINT,series_content_id BIGINT,season_number INT)");
        jdbc.execute("INSERT INTO seasons VALUES (1,10,1),(2,10,2),(3,99,1)");
        jdbc.execute("INSERT INTO episodes VALUES (101,1,1),(103,1,3),(201,2,1),(901,3,1)");
        jdbc.execute("INSERT INTO media_versions VALUES (11,NULL,101),(13,NULL,103),(21,NULL,201),(31,30,NULL)");
        jdbc.execute("INSERT INTO media_packages VALUES (1,100000)");
        jdbc.execute("INSERT INTO playback_sessions VALUES (1,'first',42,11,1,'2099-01-01'),(2,'last-season',42,13,1,'2099-01-01'),(3,'movie',42,31,1,'2099-01-01'),(4,'expired',42,21,1,'2020-01-01'),(5,'other-user',43,21,1,'2099-01-01')");
        var sessions = new AnalyticsPlaybackLookup(jdbc).find(List.of("first", "last-season", "movie", "expired", "other-user"), 42, Instant.now());
        assertThat(sessions).hasSize(3);
        assertThat(sessions.get("first").nextEpisodeId()).isEqualTo(103);
        assertThat(sessions.get("last-season").nextEpisodeId()).isEqualTo(201);
        assertThat(sessions.get("last-season").contentId()).isEqualTo(10);
        assertThat(sessions.get("movie").episodeId()).isNull();
        assertThat(sessions.get("movie").nextEpisodeId()).isNull();
        assertThat(sessions.get("movie").durationMs()).isEqualTo(100000);
    }
}
