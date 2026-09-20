package com.domain.backend.collection.application;

import com.domain.backend.collection.domain.VideoCollectionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class AdminCollectionDtos {

    private AdminCollectionDtos() {
    }

    public record CollectionRequest(
            @NotBlank @Size(max = 120) @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*$") String collectionKey,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 500) String subtitle,
            @NotNull VideoCollectionType collectionType,
            @Size(max = 200) String referenceValue,
            @Min(1) @Max(100) int itemLimit,
            int displayOrder,
            boolean enabled
    ) {
    }

    public record CollectionUpdateRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 500) String subtitle,
            @NotNull VideoCollectionType collectionType,
            @Size(max = 200) String referenceValue,
            @Min(1) @Max(100) int itemLimit,
            int displayOrder,
            boolean enabled
    ) {
    }

    public record CollectionItemsRequest(@NotNull List<@NotNull Long> videoIds) {
    }

    public record AdminCollectionResponse(
            Long collectionId,
            String collectionKey,
            String title,
            String subtitle,
            VideoCollectionType collectionType,
            String referenceValue,
            int itemLimit,
            int displayOrder,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt,
            List<AdminCollectionVideoItem> items
    ) {
    }

    public record AdminCollectionVideoItem(Long videoId, String title, int sortOrder) {
    }
}
