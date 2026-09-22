package com.domain.backend.collection.application;

import com.domain.backend.content.domain.ContentAvailabilityStatus;
import com.domain.backend.content.domain.ContentStatus;
import com.domain.backend.content.domain.ContentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class AdminCmsCollectionDtos {

    private AdminCmsCollectionDtos() {
    }

    public record CollectionRequest(
            @NotNull ContentStatus status,
            @Min(0) int minVisibleItems,
            @Valid List<CollectionLocalizationRequest> localizations
    ) {
    }

    public record CollectionUpdateRequest(
            @NotNull ContentStatus status,
            @Min(0) int minVisibleItems
    ) {
    }

    public record CollectionLocalizationRequest(
            @NotBlank @Size(max = 20) String locale,
            @NotBlank @Size(max = 300) String title,
            @Size(max = 4000) String description
    ) {
    }

    public record CollectionLocalizationUpdateRequest(
            @NotBlank @Size(max = 300) String title,
            @Size(max = 4000) String description
    ) {
    }

    public record CollectionItemsRequest(@NotEmpty List<@NotNull Long> contentIds) {
    }

    public record CollectionAvailabilityRequest(
            @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
            @NotNull Instant availableFrom,
            Instant availableUntil,
            @NotNull ContentAvailabilityStatus status
    ) {
    }

    public record CollectionAvailabilityBulkRequest(@NotEmpty @Valid List<CollectionAvailabilityRequest> availabilities) {
    }

    public record CollectionListItem(
            Long id,
            ContentStatus status,
            int minVisibleItems,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CollectionResponse(
            Long id,
            ContentStatus status,
            int minVisibleItems,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<CollectionLocalizationResponse> localizations,
            List<CollectionAvailabilityResponse> availabilities,
            List<CollectionItemResponse> items
    ) {
    }

    public record CollectionLocalizationResponse(
            Long id,
            String locale,
            String title,
            String description,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CollectionAvailabilityResponse(
            Long id,
            String countryCode,
            Instant availableFrom,
            Instant availableUntil,
            ContentAvailabilityStatus status
    ) {
    }

    public record CollectionItemResponse(
            Long id,
            Long contentId,
            ContentType type,
            ContentStatus status,
            int displayOrder,
            String title,
            String landscapeImageUrl,
            Instant createdAt
    ) {
    }

    public record CollectionPreviewResponse(
            Long collectionId,
            String countryCode,
            boolean collectionVisible,
            PreviewReason collectionReason,
            int totalItems,
            int visibleItems,
            int minVisibleItems,
            boolean displayable,
            List<CollectionPreviewItem> items
    ) {
    }

    public record CollectionPreviewItem(
            Long contentId,
            int displayOrder,
            boolean visible,
            PreviewReason reason
    ) {
    }

    public enum PreviewReason {
        COLLECTION_NOT_PUBLISHED,
        COLLECTION_TERRITORY_NOT_CONFIGURED,
        COLLECTION_TERRITORY_DISABLED,
        COLLECTION_NOT_YET_AVAILABLE,
        COLLECTION_AVAILABILITY_EXPIRED,
        CONTENT_NOT_PUBLISHED,
        CONTENT_TERRITORY_NOT_CONFIGURED,
        CONTENT_TERRITORY_DISABLED,
        CONTENT_NOT_YET_AVAILABLE,
        CONTENT_AVAILABILITY_EXPIRED
    }
}
