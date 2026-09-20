package com.domain.backend.collection.application;

import com.domain.backend.collection.domain.VideoCollectionType;
import java.util.List;

public final class PublicCollectionDtos {

    private PublicCollectionDtos() {
    }

    public record CollectionListResponse(List<CollectionResponse> collections) {
    }

    public record CollectionResponse(
            Long collectionId,
            String collectionKey,
            String title,
            String subtitle,
            VideoCollectionType collectionType,
            String referenceValue,
            List<CollectionVideoItem> videos
    ) {
    }

    public record CollectionVideoItem(Long videoId, String title, Long durationMs) {
    }
}
