package com.domain.backend.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domain.backend.content.application.AdminContentDtos.AvailabilityBulkRequest;
import com.domain.backend.content.application.AdminContentDtos.AvailabilityRequest;
import com.domain.backend.content.application.AdminContentDtos.ContentGenreRequest;
import com.domain.backend.content.application.AdminContentDtos.ContentLocalizationRequest;
import com.domain.backend.content.application.AdminContentDtos.ContentRequest;
import com.domain.backend.content.domain.ContentAvailabilityStatus;
import com.domain.backend.content.domain.ContentStatus;
import com.domain.backend.content.domain.ContentType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class AdminContentServiceIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ott_service")
            .withUsername("ott")
            .withPassword("ott");

    @Autowired
    AdminContentService contentService;

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
    void managesContentCoreAndSearchesWithFilters() {
        var movie = contentService.create(new ContentRequest(
                ContentType.MOVIE,
                ContentStatus.DRAFT,
                "KR",
                "ko",
                LocalDate.of(2026, 1, 15)
        ));
        var series = contentService.create(new ContentRequest(
                ContentType.SERIES,
                ContentStatus.PUBLISHED,
                "US",
                "en",
                LocalDate.of(2025, 5, 20)
        ));

        assertThat(movie.type()).isEqualTo(ContentType.MOVIE);
        assertThat(series.type()).isEqualTo(ContentType.SERIES);

        var localized = contentService.addLocalization(movie.id(), new ContentLocalizationRequest(
                "ko-KR",
                "오징어 게임",
                "짧은 소개",
                "긴 소개"
        ));
        localized = contentService.addLocalization(movie.id(), new ContentLocalizationRequest(
                "en-US",
                "Squid Game",
                null,
                null
        ));
        localized = contentService.addLocalization(movie.id(), new ContentLocalizationRequest(
                "ja-JP",
                "イカゲーム",
                null,
                null
        ));

        assertThat(localized.localizations()).extracting("locale").containsExactly("en-US", "ja-JP", "ko-KR");
        assertThatThrownBy(() -> contentService.addLocalization(movie.id(), new ContentLocalizationRequest(
                "ko-KR",
                "중복",
                null,
                null
        ))).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");

        var withGenres = contentService.replaceGenres(movie.id(), new ContentGenreRequest(Set.of("DRAMA", "THRILLER")));
        assertThat(withGenres.genreCodes()).containsExactly("DRAMA", "THRILLER");
        assertThatThrownBy(() -> contentService.replaceGenres(movie.id(), new ContentGenreRequest(Set.of("NO_SUCH_GENRE"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        Instant availableFrom = Instant.parse("2026-01-01T00:00:00Z");
        var available = contentService.setAvailability(movie.id(), new AvailabilityRequest(
                "KR",
                availableFrom,
                null,
                ContentAvailabilityStatus.AVAILABLE
        ));
        assertThat(available.availabilities()).extracting("countryCode").containsExactly("KR");

        var bulk = contentService.bulkSetAvailabilities(movie.id(), new AvailabilityBulkRequest(List.of(
                new AvailabilityRequest("US", availableFrom, Instant.parse("2026-12-31T00:00:00Z"), ContentAvailabilityStatus.AVAILABLE),
                new AvailabilityRequest("JP", availableFrom, null, ContentAvailabilityStatus.DISABLED)
        )));
        assertThat(bulk.availabilities()).extracting("countryCode").containsExactly("JP", "KR", "US");
        assertThatThrownBy(() -> contentService.setAvailability(movie.id(), new AvailabilityRequest(
                "GB",
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                ContentAvailabilityStatus.AVAILABLE
        ))).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        contentService.addLocalization(series.id(), new ContentLocalizationRequest(
                "en-US",
                "Space Comedy",
                null,
                null
        ));
        contentService.replaceGenres(series.id(), new ContentGenreRequest(Set.of("COMEDY", "SF")));
        contentService.setAvailability(series.id(), new AvailabilityRequest(
                "US",
                availableFrom,
                null,
                ContentAvailabilityStatus.AVAILABLE
        ));

        assertThat(contentService.list(movie.id().toString(), null, null, null, null, 0, 20).content())
                .extracting("id")
                .containsExactly(movie.id());
        assertThat(contentService.list("Squid", null, null, null, null, 0, 20).content())
                .extracting("id")
                .containsExactly(movie.id());
        assertThat(contentService.list(null, ContentType.MOVIE, null, null, null, 0, 20).content())
                .extracting("id")
                .contains(movie.id())
                .doesNotContain(series.id());
        assertThat(contentService.list(null, null, "THRILLER", null, null, 0, 20).content())
                .extracting("id")
                .containsExactly(movie.id());
        assertThat(contentService.list(null, null, null, "JP", null, 0, 20).content())
                .extracting("id")
                .containsExactly(movie.id());
        assertThat(contentService.list(null, null, null, null, ContentStatus.PUBLISHED, 0, 20).content())
                .extracting("id")
                .contains(series.id())
                .doesNotContain(movie.id());

        var firstPage = contentService.list(null, null, null, null, null, 0, 1);
        var secondPage = contentService.list(null, null, null, null, null, 1, 1);
        assertThat(firstPage.size()).isEqualTo(1);
        assertThat(firstPage.totalElements()).isGreaterThanOrEqualTo(2);
        assertThat(secondPage.page()).isEqualTo(1);
        assertThat(secondPage.content()).hasSize(1);
    }
}
