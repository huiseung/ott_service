package com.domain.backend.video.application;

import com.domain.backend.video.domain.VideoFileStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class VideoUploadDtos {

    private VideoUploadDtos() {
    }

    public record CreateVideoRequest(
            @NotBlank @Size(max = 300) String title,
            @Valid @NotNull FileRequest file
    ) {
    }

    public record FileRequest(
            @NotBlank @Size(max = 500) String fileName,
            @Min(1) long fileSize,
            @NotBlank @Size(max = 255) String contentType,
            @NotBlank @Size(max = 255) String fingerprint
    ) {
    }

    public record CreateVideoResponse(
            Long videoId,
            Long videoFileId,
            long partSize,
            int totalParts,
            VideoFileStatus status
    ) {
    }

    public record PresignPartsRequest(@NotEmpty List<@Min(1) Integer> partNumbers) {
    }

    public record PresignPartsResponse(List<PresignedPartResponse> parts, Instant expiresAt) {
    }

    public record PresignedPartResponse(int partNumber, String url) {
    }

    public record AckPartsRequest(@NotEmpty @Valid List<AckPartRequest> parts) {
    }

    public record AckPartRequest(@Min(1) int partNumber, @NotBlank String etag, @Min(1) long size) {
    }

    public record UploadStatusResponse(
            Long videoFileId,
            VideoFileStatus status,
            long partSize,
            int totalParts,
            String fingerprint,
            List<Integer> confirmedParts,
            List<Integer> retryParts
    ) {
    }
}
