package com.domain.backend.collection.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionAvailabilityBulkRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionAvailabilityRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionItemsRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionLocalizationRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionLocalizationUpdateRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionPreviewItem;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionUpdateRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.PreviewReason;
import com.domain.backend.content.application.AdminContentDtos.AvailabilityRequest;
import com.domain.backend.content.application.AdminContentDtos.ContentLocalizationRequest;
import com.domain.backend.content.application.AdminContentDtos.ContentRequest;
import com.domain.backend.content.application.AdminContentService;
import com.domain.backend.content.domain.ContentAvailabilityStatus;
import com.domain.backend.content.domain.ContentStatus;
import com.domain.backend.content.domain.ContentType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class AdminCmsCollectionServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ott_service")
            .withUsername("ott")
            .withPassword("ott");

    @Autowired
    AdminCmsCollectionService collectionService;

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

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Test
    void managesCollectionLocalizationsListAndSearch() {
        var collection = collectionService.create(new CollectionRequest(
                ContentStatus.DRAFT,
                0,
                List.of(
                        new CollectionLocalizationRequest("ko-KR", "지금 인기 있는 작품", "설명"),
                        new CollectionLocalizationRequest("en-US", "Popular Now", null)
                )
        ));

        assertThat(collection.localizations()).extracting("locale").containsExactly("en-US", "ko-KR");
        assertThatThrownBy(() -> collectionService.addLocalization(collection.id(),
                new CollectionLocalizationRequest("ko-KR", "중복", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");

        var updated = collectionService.updateLocalization(collection.id(), "en-US",
                new CollectionLocalizationUpdateRequest("Popular This Week", "Updated"));
        assertThat(updated.localizations()).filteredOn(localization -> localization.locale().equals("en-US"))
                .extracting("title")
                .containsExactly("Popular This Week");

        assertThat(collectionService.list("Popular", null, null, 0, 20).content())
                .extracting("id")
                .contains(collection.id());
        assertThat(collectionService.list(null, ContentStatus.DRAFT, null, 0, 20).content())
                .extracting("id")
                .contains(collection.id());
    }

    @Test
    void addsRemovesAndReordersItems() {
        Long a = content("A", ContentStatus.PUBLISHED);
        Long b = content("B", ContentStatus.PUBLISHED);
        Long c = content("C", ContentStatus.PUBLISHED);
        Long d = content("D", ContentStatus.PUBLISHED);
        var collection = collectionService.create(new CollectionRequest(ContentStatus.DRAFT, 0, List.of()));

        var withItems = collectionService.addItems(collection.id(), new CollectionItemsRequest(List.of(a, b)));
        withItems = collectionService.addItems(collection.id(), new CollectionItemsRequest(List.of(c, d)));
        assertThat(withItems.items()).extracting("contentId").containsExactly(a, b, c, d);
        assertThat(withItems.items()).extracting("displayOrder").containsExactly(1, 2, 3, 4);

        assertThatThrownBy(() -> collectionService.addItems(collection.id(), new CollectionItemsRequest(List.of(a))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        assertThatThrownBy(() -> collectionService.addItems(collection.id(), new CollectionItemsRequest(List.of(999_999L))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        var removed = collectionService.removeItem(collection.id(), b);
        assertThat(removed.items()).extracting("contentId").containsExactly(a, c, d);
        assertThat(removed.items()).extracting("displayOrder").containsExactly(1, 2, 3);

        collectionService.addItems(collection.id(), new CollectionItemsRequest(List.of(b)));
        var reordered = collectionService.reorder(collection.id(), new CollectionItemsRequest(List.of(d, b, a, c)));
        assertThat(reordered.items()).extracting("contentId").containsExactly(d, b, a, c);
        assertThat(reordered.items()).extracting("displayOrder").containsExactly(1, 2, 3, 4);

        assertThatThrownBy(() -> collectionService.reorder(collection.id(), new CollectionItemsRequest(List.of(d, d, a, c))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> collectionService.reorder(collection.id(), new CollectionItemsRequest(List.of(d, b, a))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> collectionService.reorder(collection.id(), new CollectionItemsRequest(List.of(d, b, a, 999_999L))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        Long other = content("Other", ContentStatus.PUBLISHED);
        var otherCollection = collectionService.create(new CollectionRequest(ContentStatus.DRAFT, 0, List.of()));
        collectionService.addItems(otherCollection.id(), new CollectionItemsRequest(List.of(other)));
        assertThatThrownBy(() -> collectionService.reorder(collection.id(), new CollectionItemsRequest(List.of(d, b, a, other))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void managesCollectionAvailability() {
        var collection = collectionService.create(new CollectionRequest(ContentStatus.PUBLISHED, 0, List.of()));
        Instant from = NOW.minusSeconds(3600);

        var kr = collectionService.setAvailability(collection.id(), new CollectionAvailabilityRequest(
                "KR",
                from,
                null,
                ContentAvailabilityStatus.AVAILABLE
        ));
        assertThat(kr.availabilities()).extracting("countryCode").containsExactly("KR");

        var bulk = collectionService.bulkSetAvailabilities(collection.id(), new CollectionAvailabilityBulkRequest(List.of(
                new CollectionAvailabilityRequest("JP", from, null, ContentAvailabilityStatus.AVAILABLE),
                new CollectionAvailabilityRequest("US", from, null, ContentAvailabilityStatus.DISABLED)
        )));
        assertThat(bulk.availabilities()).extracting("countryCode").containsExactly("JP", "KR", "US");
        assertThat(bulk.availabilities()).filteredOn(availability -> availability.countryCode().equals("US"))
                .extracting("status")
                .containsExactly(ContentAvailabilityStatus.DISABLED);

        assertThatThrownBy(() -> collectionService.setAvailability(collection.id(), new CollectionAvailabilityRequest(
                "GB",
                NOW,
                NOW.minusSeconds(1),
                ContentAvailabilityStatus.AVAILABLE
        ))).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        collectionService.deleteAvailability(collection.id(), "JP");
        assertThat(collectionService.listAvailabilities(collection.id())).extracting("countryCode")
                .containsExactly("KR", "US");
    }

    @Test
    void previewsTerritoryVisibilityAndMinVisibleItems() {
        Long visible = content("Visible", ContentStatus.PUBLISHED);
        Long noTerritory = content("No Territory", ContentStatus.PUBLISHED);
        Long draft = content("Draft", ContentStatus.DRAFT);
        Long future = content("Future", ContentStatus.PUBLISHED);
        setContentAvailability(visible, "KR", NOW.minusSeconds(3600), null, ContentAvailabilityStatus.AVAILABLE);
        setContentAvailability(draft, "KR", NOW.minusSeconds(3600), null, ContentAvailabilityStatus.AVAILABLE);
        setContentAvailability(future, "KR", Instant.parse("2030-01-01T00:00:00Z"), null, ContentAvailabilityStatus.AVAILABLE);

        var collection = collectionService.create(new CollectionRequest(ContentStatus.PUBLISHED, 1, List.of()));
        collectionService.addItems(collection.id(), new CollectionItemsRequest(List.of(visible, noTerritory, draft, future)));
        collectionService.setAvailability(collection.id(), new CollectionAvailabilityRequest(
                "KR",
                NOW.minusSeconds(3600),
                null,
                ContentAvailabilityStatus.AVAILABLE
        ));

        var preview = collectionService.preview(collection.id(), "KR");
        assertThat(preview.collectionVisible()).isTrue();
        assertThat(preview.totalItems()).isEqualTo(4);
        assertThat(preview.visibleItems()).isEqualTo(1);
        assertThat(preview.displayable()).isTrue();

        Map<Long, PreviewReason> reasonByContentId = new HashMap<>();
        for (CollectionPreviewItem item : preview.items()) {
            reasonByContentId.put(item.contentId(), item.reason());
        }
        assertThat(reasonByContentId.get(visible)).isNull();
        assertThat(reasonByContentId.get(noTerritory)).isEqualTo(PreviewReason.CONTENT_TERRITORY_NOT_CONFIGURED);
        assertThat(reasonByContentId.get(draft)).isEqualTo(PreviewReason.CONTENT_NOT_PUBLISHED);
        assertThat(reasonByContentId.get(future)).isEqualTo(PreviewReason.CONTENT_NOT_YET_AVAILABLE);

        collectionService.update(collection.id(), new CollectionUpdateRequest(ContentStatus.PUBLISHED, 2));
        assertThat(collectionService.preview(collection.id(), "KR").displayable()).isFalse();
        collectionService.addItems(collection.id(), new CollectionItemsRequest(List.of(content("Second", ContentStatus.PUBLISHED))));
        Long second = collectionService.get(collection.id()).items().getLast().contentId();
        setContentAvailability(second, "KR", NOW.minusSeconds(3600), null, ContentAvailabilityStatus.AVAILABLE);
        assertThat(collectionService.preview(collection.id(), "KR").displayable()).isTrue();

        collectionService.update(collection.id(), new CollectionUpdateRequest(ContentStatus.DRAFT, 2));
        var draftPreview = collectionService.preview(collection.id(), "KR");
        assertThat(draftPreview.collectionVisible()).isFalse();
        assertThat(draftPreview.collectionReason()).isEqualTo(PreviewReason.COLLECTION_NOT_PUBLISHED);

        collectionService.update(collection.id(), new CollectionUpdateRequest(ContentStatus.PUBLISHED, 2));
        collectionService.setAvailability(collection.id(), new CollectionAvailabilityRequest(
                "JP",
                NOW.minusSeconds(3600),
                null,
                ContentAvailabilityStatus.DISABLED
        ));
        var disabledPreview = collectionService.preview(collection.id(), "JP");
        assertThat(disabledPreview.collectionVisible()).isFalse();
        assertThat(disabledPreview.collectionReason()).isEqualTo(PreviewReason.COLLECTION_TERRITORY_DISABLED);
    }

    private Long content(String title, ContentStatus status) {
        var content = contentService.create(new ContentRequest(
                ContentType.MOVIE,
                status,
                "KR",
                "ko",
                LocalDate.of(2026, 1, 1)
        ));
        contentService.addLocalization(content.id(), new ContentLocalizationRequest("ko-KR", title, null, null));
        return content.id();
    }

    private void setContentAvailability(Long contentId, String countryCode, Instant from, Instant until,
                                        ContentAvailabilityStatus status) {
        contentService.setAvailability(contentId, new AvailabilityRequest(countryCode, from, until, status));
    }
}
