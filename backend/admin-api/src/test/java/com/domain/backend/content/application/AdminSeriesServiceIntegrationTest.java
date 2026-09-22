package com.domain.backend.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domain.backend.content.application.AdminContentDtos.ContentRequest;
import com.domain.backend.content.application.AdminSeriesDtos.AttachVideoRequest;
import com.domain.backend.content.application.AdminSeriesDtos.EpisodeLocalizationRequest;
import com.domain.backend.content.application.AdminSeriesDtos.EpisodeLocalizationUpdateRequest;
import com.domain.backend.content.application.AdminSeriesDtos.EpisodeRequest;
import com.domain.backend.content.application.AdminSeriesDtos.MediaVersionRequest;
import com.domain.backend.content.application.AdminSeriesDtos.SeasonRequest;
import com.domain.backend.content.domain.ContentStatus;
import com.domain.backend.content.domain.ContentType;
import com.domain.backend.content.domain.MediaVersion;
import com.domain.backend.content.domain.MediaVersionType;
import com.domain.backend.content.infrastructure.persistence.MediaVersionRepository;
import com.domain.backend.video.domain.Video;
import com.domain.backend.video.infrastructure.persistence.VideoRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class AdminSeriesServiceIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ott_service")
            .withUsername("ott")
            .withPassword("ott");

    @Autowired
    AdminContentService contentService;

    @Autowired
    AdminSeriesService seriesService;

    @Autowired
    VideoRepository videoRepository;

    @Autowired
    MediaVersionRepository mediaVersionRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("app.security.admin-password", () -> "test-admin-password");
        registry.add("app.storage.bucket", () -> "ott-originals-test");
        registry.add("app.storage.access-key", () -> "minio");
        registry.add("app.storage.secret-key", () -> "password");
    }

    @Test
    void managesSeriesSeasonsEpisodesAndMediaVersions() {
        var movie = createContent(ContentType.MOVIE, "Movie");
        var series = createContent(ContentType.SERIES, "Series");

        assertThatThrownBy(() -> seriesService.createSeason(movie.id(), new SeasonRequest(1, ContentStatus.DRAFT)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> seriesService.createSeason(series.id(), new SeasonRequest(0, ContentStatus.DRAFT)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        var season = seriesService.createSeason(series.id(), new SeasonRequest(1, ContentStatus.DRAFT));
        assertThat(season.seriesContentId()).isEqualTo(series.id());
        assertThatThrownBy(() -> seriesService.createSeason(series.id(), new SeasonRequest(1, ContentStatus.DRAFT)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");

        var episode2 = seriesService.createEpisode(season.id(), new EpisodeRequest(2, ContentStatus.DRAFT, null));
        var episode1 = seriesService.createEpisode(season.id(), new EpisodeRequest(1, ContentStatus.DRAFT, null));
        assertThatThrownBy(() -> seriesService.createEpisode(season.id(), new EpisodeRequest(1, ContentStatus.DRAFT, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        assertThatThrownBy(() -> seriesService.createEpisode(season.id(), new EpisodeRequest(0, ContentStatus.DRAFT, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThat(seriesService.listEpisodes(season.id())).extracting("episodeNumber").containsExactly(1, 2);

        var localized = seriesService.addEpisodeLocalization(episode1.id(), new EpisodeLocalizationRequest(
                "ko-KR",
                "제1화",
                "첫 번째 에피소드"
        ));
        localized = seriesService.addEpisodeLocalization(episode1.id(), new EpisodeLocalizationRequest(
                "en-US",
                "Episode 1",
                "First episode"
        ));
        assertThat(localized.localizations()).extracting("locale").containsExactly("en-US", "ko-KR");
        assertThatThrownBy(() -> seriesService.addEpisodeLocalization(episode1.id(), new EpisodeLocalizationRequest(
                "ko-KR",
                "중복",
                null
        ))).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");

        var updated = seriesService.updateEpisodeLocalization(episode1.id(), "en-US",
                new EpisodeLocalizationUpdateRequest("Episode One", "Updated"));
        assertThat(updated.localizations()).filteredOn(localization -> localization.locale().equals("en-US"))
                .extracting("title")
                .containsExactly("Episode One");
        seriesService.deleteEpisodeLocalization(episode1.id(), "ko-KR");
        assertThat(seriesService.getEpisode(episode1.id()).localizations()).extracting("locale").containsExactly("en-US");

        var movieVersion = seriesService.createContentMediaVersion(movie.id(),
                new MediaVersionRequest(MediaVersionType.ORIGINAL, ContentStatus.DRAFT));
        assertThat(movieVersion.contentId()).isEqualTo(movie.id());
        assertThat(movieVersion.episodeId()).isNull();
        assertThatThrownBy(() -> seriesService.createContentMediaVersion(series.id(),
                new MediaVersionRequest(MediaVersionType.ORIGINAL, ContentStatus.DRAFT)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        var episodeVersion = seriesService.createEpisodeMediaVersion(episode1.id(),
                new MediaVersionRequest(MediaVersionType.ORIGINAL, ContentStatus.DRAFT));
        assertThat(episodeVersion.contentId()).isNull();
        assertThat(episodeVersion.episodeId()).isEqualTo(episode1.id());

        assertThatThrownBy(() -> mediaVersionRepository.saveAndFlush(new MediaVersion(
                movie.id(),
                episode1.id(),
                MediaVersionType.CENSORED,
                ContentStatus.DRAFT
        ))).isInstanceOf(DataIntegrityViolationException.class);

        Video video = videoRepository.save(new Video("Uploaded asset"));
        var attached = seriesService.attachVideo(movieVersion.id(), new AttachVideoRequest(video.getId()));
        assertThat(attached.videoId()).isEqualTo(video.getId());
        assertThat(attached.video().title()).isEqualTo("Uploaded asset");

        assertThatThrownBy(() -> seriesService.attachVideo(episodeVersion.id(), new AttachVideoRequest(999_999L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThatThrownBy(() -> seriesService.attachVideo(movieVersion.id(), new AttachVideoRequest(video.getId())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        assertThatThrownBy(() -> seriesService.attachVideo(episodeVersion.id(), new AttachVideoRequest(video.getId())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");

        assertThat(seriesService.listContentMediaVersions(movie.id())).extracting("id").containsExactly(movieVersion.id());
        assertThat(seriesService.listEpisodeMediaVersions(episode1.id())).extracting("id").containsExactly(episodeVersion.id());
    }

    private AdminContentDtos.ContentResponse createContent(ContentType type, String title) {
        var content = contentService.create(new ContentRequest(
                type,
                ContentStatus.DRAFT,
                "KR",
                "ko",
                LocalDate.of(2026, 1, 1)
        ));
        contentService.addLocalization(content.id(), new AdminContentDtos.ContentLocalizationRequest(
                "ko-KR",
                title,
                null,
                null
        ));
        return contentService.get(content.id());
    }
}
