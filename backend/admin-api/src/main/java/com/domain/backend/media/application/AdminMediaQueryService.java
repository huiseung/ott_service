package com.domain.backend.media.application;

import com.domain.backend.media.application.AdminMediaDtos.AdminVideoListItem;
import com.domain.backend.media.application.AdminMediaDtos.AdminVideoDetailResponse;
import com.domain.backend.media.application.AdminMediaDtos.MediaPackageSummary;
import com.domain.backend.media.application.AdminMediaDtos.MediaProcessingJobListItem;
import com.domain.backend.media.application.AdminMediaDtos.PageResponse;
import com.domain.backend.media.application.AdminMediaDtos.Processing;
import com.domain.backend.media.application.AdminMediaDtos.Source;
import com.domain.backend.media.domain.MediaProcessingJob;
import com.domain.backend.media.domain.MediaProcessingStage;
import com.domain.backend.media.domain.MediaProcessingJobStatus;
import com.domain.backend.media.infrastructure.persistence.MediaPackageRepository;
import com.domain.backend.media.infrastructure.persistence.MediaProcessingJobRepository;
import com.domain.backend.video.domain.Video;
import com.domain.backend.video.domain.VideoStatus;
import com.domain.backend.video.infrastructure.persistence.VideoFileRepository;
import com.domain.backend.video.infrastructure.persistence.VideoRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminMediaQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final VideoRepository videoRepository;
    private final VideoFileRepository videoFileRepository;
    private final MediaProcessingJobRepository jobRepository;
    private final MediaPackageRepository mediaPackageRepository;
    private final JdbcTemplate jdbcTemplate;

    public AdminMediaQueryService(VideoRepository videoRepository, VideoFileRepository videoFileRepository,
                                  MediaProcessingJobRepository jobRepository,
                                  MediaPackageRepository mediaPackageRepository,
                                  JdbcTemplate jdbcTemplate) {
        this.videoRepository = videoRepository;
        this.videoFileRepository = videoFileRepository;
        this.jobRepository = jobRepository;
        this.mediaPackageRepository = mediaPackageRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public AdminVideoDetailResponse getVideo(Long videoId) {
        var video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));
        Source source = null;
        if (video.getActiveVideoFileId() != null) {
            var videoFile = videoFileRepository.findById(video.getActiveVideoFileId()).orElse(null);
            if (videoFile != null) {
                source = new Source(videoFile.getId(), videoFile.getStatus());
            }
        }
        Processing processing = jobRepository.findTopByVideoIdOrderByCreatedAtDesc(videoId)
                .map(job -> new Processing(
                        job.getId(),
                        job.getStatus(),
                        job.getStage(),
                        job.getProgressPercent(),
                        job.getProcessedMs(),
                        job.getDurationMs(),
                        job.getAttempt(),
                        job.getErrorCode()
                ))
                .orElse(null);
        MediaPackageSummary mediaPackage = null;
        if (video.getPublishedMediaPackageId() != null) {
            var published = mediaPackageRepository.findById(video.getPublishedMediaPackageId()).orElse(null);
            if (published != null) {
                mediaPackage = new MediaPackageSummary(published.getId(), published.getProfileVersion());
            }
        }
        return new AdminVideoDetailResponse(video.getId(), video.getTitle(), video.getStatus(), source, processing, mediaPackage);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminVideoListItem> listVideos(VideoStatus status, Integer page, Integer size) {
        var pageable = pageRequest(page, size);
        Page<Video> videos = status == null
                ? videoRepository.findAll(pageable)
                : videoRepository.findByStatus(status, pageable);
        Map<Long, Processing> latestProcessingByVideoId = latestProcessingByVideoIds(
                videos.getContent().stream().map(Video::getId).toList()
        );
        return pageResponse(videos.map(video -> new AdminVideoListItem(
                video.getId(),
                video.getTitle(),
                video.getStatus(),
                video.getActiveVideoFileId(),
                video.getPublishedMediaPackageId(),
                video.getCreatedAt(),
                video.getUpdatedAt(),
                latestProcessingByVideoId.get(video.getId())
        )));
    }

    @Transactional(readOnly = true)
    public PageResponse<MediaProcessingJobListItem> listProcessingJobs(
            MediaProcessingJobStatus status,
            Long videoId,
            Integer page,
            Integer size
    ) {
        var jobs = jobRepository.search(status, videoId, pageRequest(page, size));
        return pageResponse(jobs.map(this::toJobListItem));
    }

    private Processing latestProcessing(Long videoId) {
        return jobRepository.findTopByVideoIdOrderByCreatedAtDesc(videoId)
                .map(job -> new Processing(
                        job.getId(),
                        job.getStatus(),
                        job.getStage(),
                        job.getProgressPercent(),
                        job.getProcessedMs(),
                        job.getDurationMs(),
                        job.getAttempt(),
                        job.getErrorCode()
                ))
                .orElse(null);
    }

    private Map<Long, Processing> latestProcessingByVideoIds(List<Long> videoIds) {
        if (videoIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = videoIds.stream().map(ignored -> "?").collect(Collectors.joining(", "));
        return jdbcTemplate.query("""
                        select video_id, id, status, stage, progress_percent, processed_ms, duration_ms, attempt, error_code
                        from (
                            select j.*,
                                   row_number() over(partition by j.video_id order by j.created_at desc, j.id desc) as rn
                            from media_processing_jobs j
                            where j.video_id in (%s)
                        ) latest
                        where rn = 1
                        """.formatted(placeholders),
                rs -> {
                    Map<Long, Processing> result = new java.util.HashMap<>();
                    while (rs.next()) {
                        result.put(rs.getLong("video_id"), new Processing(
                                rs.getLong("id"),
                                MediaProcessingJobStatus.valueOf(rs.getString("status")),
                                MediaProcessingStage.valueOf(rs.getString("stage")),
                                rs.getInt("progress_percent"),
                                (Long) rs.getObject("processed_ms"),
                                (Long) rs.getObject("duration_ms"),
                                rs.getInt("attempt"),
                                rs.getString("error_code")
                        ));
                    }
                    return result;
                },
                videoIds.toArray());
    }

    private MediaProcessingJobListItem toJobListItem(MediaProcessingJob job) {
        return new MediaProcessingJobListItem(
                job.getId(),
                job.getJobKey(),
                job.getVideoId(),
                job.getVideoFileId(),
                job.getProfileVersion(),
                job.getStatus(),
                job.getStage(),
                job.getProgressPercent(),
                job.getProcessedMs(),
                job.getDurationMs(),
                job.getAttempt(),
                job.getGeneration(),
                job.getWorkerId(),
                job.getLeaseUntil(),
                job.getLastHeartbeatAt(),
                job.getNextRunAt(),
                job.getErrorCode(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getUpdatedAt()
        );
    }

    private PageRequest pageRequest(Integer page, Integer size) {
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private <T> PageResponse<T> pageResponse(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
