package com.domain.backend.content.application;

import com.domain.backend.content.domain.ContentStatus;
import com.domain.backend.content.domain.MediaVersionType;
import com.domain.backend.video.domain.VideoStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class AdminSeriesDtos {

    private AdminSeriesDtos() {
    }

    public record SeasonRequest(
            @Min(1) int seasonNumber,
            @NotNull ContentStatus status
    ) {
    }

    public record SeasonResponse(
            Long id,
            Long seriesContentId,
            int seasonNumber,
            ContentStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record EpisodeRequest(
            @Min(1) int episodeNumber,
            @NotNull ContentStatus status,
            Instant releaseAt
    ) {
    }

    public record EpisodeResponse(
            Long id,
            Long seasonId,
            int episodeNumber,
            ContentStatus status,
            Instant releaseAt,
            Instant createdAt,
            Instant updatedAt,
            List<EpisodeLocalizationResponse> localizations
    ) {
    }

    public record EpisodeLocalizationRequest(
            @NotBlank @Size(max = 20) String locale,
            @NotBlank @Size(max = 300) String title,
            @Size(max = 4000) String description
    ) {
    }

    public record EpisodeLocalizationUpdateRequest(
            @NotBlank @Size(max = 300) String title,
            @Size(max = 4000) String description
    ) {
    }

    public record EpisodeLocalizationResponse(
            Long id,
            String locale,
            String title,
            String description,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record MediaVersionRequest(
            @NotNull MediaVersionType versionType,
            @NotNull ContentStatus status
    ) {
    }

    public record AttachVideoRequest(@NotNull Long videoId) {
    }

    public record MediaVersionResponse(
            Long id,
            Long contentId,
            Long episodeId,
            Long videoId,
            MediaVersionType versionType,
            ContentStatus status,
            Instant createdAt,
            Instant updatedAt,
            VideoSummary video
    ) {
    }

    public record VideoSummary(
            Long id,
            String title,
            VideoStatus status,
            Long activeVideoFileId,
            Long publishedMediaPackageId
    ) {
    }
}
