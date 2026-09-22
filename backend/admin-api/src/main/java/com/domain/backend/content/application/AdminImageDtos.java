package com.domain.backend.content.application;

import com.domain.backend.content.domain.ContentImageType;
import com.domain.backend.content.domain.EpisodeImageType;
import java.time.Instant;
import java.util.List;

public final class AdminImageDtos {

    private AdminImageDtos() {
    }

    public record ContentImagesResponse(List<ContentImageResponse> images) {
    }

    public record EpisodeImagesResponse(List<EpisodeImageResponse> images) {
    }

    public record ContentImageResponse(
            ContentImageType type,
            String url,
            int width,
            int height,
            long fileSize,
            String mimeType,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record EpisodeImageResponse(
            EpisodeImageType type,
            String url,
            int width,
            int height,
            long fileSize,
            String mimeType,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
