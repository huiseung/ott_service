package com.domain.backend.media.application;

import com.domain.backend.media.domain.MediaProcessingJobStatus;
import com.domain.backend.media.domain.MediaProcessingStage;
import com.domain.backend.video.domain.VideoFileStatus;
import com.domain.backend.video.domain.VideoStatus;
import java.time.Instant;
import java.util.List;

public final class AdminMediaDtos {

    private AdminMediaDtos() {
    }

    public record AdminVideoDetailResponse(
            Long id,
            String title,
            VideoStatus status,
            Source source,
            Processing processing,
            MediaPackageSummary mediaPackage
    ) {
    }

    public record Source(Long videoFileId, VideoFileStatus status) {
    }

    public record Processing(
            Long jobId,
            MediaProcessingJobStatus status,
            MediaProcessingStage stage,
            int progressPercent,
            Long processedMs,
            Long durationMs,
            int attempt,
            String errorCode
    ) {
    }

    public record MediaPackageSummary(Long id, String profileVersion) {
    }

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {
    }

    public record AdminVideoListItem(
            Long id,
            String title,
            VideoStatus status,
            Long activeVideoFileId,
            Long publishedMediaPackageId,
            Instant createdAt,
            Instant updatedAt,
            Processing processing
    ) {
    }

    public record MediaProcessingJobListItem(
            Long id,
            String jobKey,
            Long videoId,
            Long videoFileId,
            String profileVersion,
            MediaProcessingJobStatus status,
            MediaProcessingStage stage,
            int progressPercent,
            Long processedMs,
            Long durationMs,
            int attempt,
            int generation,
            String workerId,
            Instant leaseUntil,
            Instant lastHeartbeatAt,
            Instant nextRunAt,
            String errorCode,
            String errorMessage,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant updatedAt
    ) {
    }
}
