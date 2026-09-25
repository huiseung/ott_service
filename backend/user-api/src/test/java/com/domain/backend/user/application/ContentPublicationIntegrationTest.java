package com.domain.backend.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ContentPublicationIntegrationTest {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");
    static JdbcTemplate jdbc;
    static final Instant START = Instant.parse("2026-09-23T00:00:00Z");
    static final Instant END = START.plusSeconds(3600);

    @BeforeAll
    static void migrate() {
        var source = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
    }

    @BeforeEach
    void fixture() {
        jdbc.update("delete from video_collections");
        jdbc.update("delete from media_versions");
        jdbc.update("delete from contents");
        jdbc.update("update videos set published_media_package_id = null");
        jdbc.update("delete from media_packages");
        jdbc.update("delete from video_files");
        jdbc.update("delete from videos");
        jdbc.update("insert into contents(id, type, status, original_country, original_language, created_at, updated_at) values (1, 'MOVIE', 'PUBLISHED', 'US', 'en', now(), now())");
        jdbc.update("insert into content_localizations(content_id, locale, title, created_at, updated_at) values (1, 'ko-KR', '작품 제목', now(), now())");
        jdbc.update("insert into content_availabilities(content_id, country_code, available_from, available_until, status) values (1, 'KR', ?, ?, 'AVAILABLE')", Timestamp.from(START), Timestamp.from(END));
        video(10);
        jdbc.update("insert into media_versions(id, content_id, video_id, version_type, status, created_at, updated_at) values (1, 1, 10, 'ORIGINAL', 'PUBLISHED', now(), now())");
    }

    @Test
    void exposesContentOnlyInsideHalfOpenServiceWindow() {
        assertUnavailable(START.minusNanos(1000));
        assertThat(catalog(START).list(null, 20).items()).extracting("title").containsExactly("작품 제목");
        eligibility(START).requirePlayable(10L);
        assertThat(catalog(END.minusNanos(1000)).list(null, 20).items()).hasSize(1);
        assertUnavailable(END);
        assertUnavailable(END.plusSeconds(1));
    }

    @Test
    void excludesUnlinkedDraftDisabledAndWrongCountryVideos() {
        video(11);
        assertThat(new PublicVideoQueryService(jdbc, eligibility(START)).listVideos(null, 20).items())
                .extracting("videoId").containsExactly(10L);
        assertThatThrownBy(() -> eligibility(START).requirePlayable(11L)).isInstanceOf(ResponseStatusException.class);
        for (String update : new String[]{
                "update contents set status = 'DRAFT'",
                "update media_versions set status = 'DRAFT'",
                "update content_availabilities set status = 'DISABLED'",
                "update content_availabilities set country_code = 'US'",
                "update videos set status = 'PROCESSING'",
                "update media_packages set status = 'FAILED'"}) {
            fixture();
            jdbc.update(update);
            assertUnavailable(START);
        }
    }

    @Test
    void supportsOpenEndedAvailabilityAndDeduplicatesEditingVersions() {
        jdbc.update("update content_availabilities set available_until = null");
        video(11);
        jdbc.update("insert into media_versions(content_id, video_id, version_type, status, created_at, updated_at) values (1, 11, 'DIRECTORS_CUT', 'PUBLISHED', now(), now())");
        var page = catalog(END.plusSeconds(1)).list(null, 1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().videoId()).isEqualTo(10L);
        assertThat(page.hasNext()).isFalse();
        assertThat(catalog(START).list(1L, 20).items()).isEmpty();
    }

    @Test
    void seriesRequiresPublishedHierarchyAndEpisodeRelease() {
        jdbc.update("update contents set type = 'SERIES'");
        jdbc.update("insert into seasons(id, series_content_id, season_number, status, created_at, updated_at) values (1, 1, 1, 'PUBLISHED', now(), now())");
        jdbc.update("insert into episodes(id, season_id, episode_number, status, release_at, created_at, updated_at) values (1, 1, 1, 'PUBLISHED', ?, now(), now())", Timestamp.from(START.plusSeconds(1)));
        jdbc.update("update media_versions set content_id = null, episode_id = 1");
        assertUnavailable(START);
        assertThat(catalog(START.plusSeconds(1)).list(null, 20).items()).hasSize(1);
        jdbc.update("update seasons set status = 'DRAFT'");
        assertUnavailable(START.plusSeconds(1));
    }

    private void assertUnavailable(Instant now) {
        assertThat(catalog(now).list(null, 20).items()).isEmpty();
        assertThat(new PublicVideoQueryService(jdbc, eligibility(now)).listVideos(null, 20).items()).isEmpty();
        assertThatThrownBy(() -> eligibility(now).requirePlayable(10L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> new PublicVideoQueryService(jdbc, eligibility(now)).getVideo(10L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void legacyCollectionsFilterUnavailableVideosBeforeApplyingLimit() {
        video(11);
        jdbc.update("insert into video_collections(id, collection_key, title, collection_type, item_limit, display_order, enabled, created_at, updated_at) values (1, 'test', 'test', 'MANUAL', 1, 1, true, now(), now())");
        jdbc.update("insert into video_collection_items(collection_id, video_id, sort_order, added_at) values (1, 11, 0, now()), (1, 10, 1, now())");
        var service = new com.domain.backend.collection.application.PublicCollectionQueryService(jdbc, eligibility(START));
        assertThat(service.listCollectionVideos(1L, 1)).extracting("videoId").containsExactly(10L);
        var expired = new com.domain.backend.collection.application.PublicCollectionQueryService(jdbc, eligibility(END));
        assertThat(expired.listCollectionVideos(1L, 1)).isEmpty();
    }

    private ContentEligibility eligibility(Instant now) {
        return new ContentEligibility(jdbc, "KR", Clock.fixed(now, ZoneOffset.UTC));
    }

    private PublicContentQueryService catalog(Instant now) {
        return new PublicContentQueryService(jdbc, eligibility(now));
    }

    private void video(long id) {
        jdbc.update("insert into videos(id, title, status, created_at, updated_at) values (?, 'upload filename', 'READY', now(), now())", id);
        jdbc.update("insert into video_files(id, video_id, generation, original_filename, content_type, file_size, fingerprint, object_key, part_size, total_parts, status, created_at, updated_at) values (?, ?, 1, 'test.mp4', 'video/mp4', 1, 'fixture', ?, 1, 1, 'COMPLETED', now(), now())", id, id, "fixture/" + id);
        jdbc.update("insert into media_packages(id, video_id, source_video_file_id, profile_version, root_key, master_manifest_key, duration_ms, status, created_at) values (?, ?, ?, 'test', 'test', 'test/master.m3u8', 1000, 'READY', now())", id, id, id);
        jdbc.update("update videos set published_media_package_id = ? where id = ?", id, id);
    }
}
