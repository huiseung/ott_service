package com.domain.backend.worker.job;

import com.domain.backend.media.domain.MediaProcessingStage;
import com.domain.backend.worker.config.MediaWorkerProperties;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MediaJobStateService {

    private final JdbcTemplate jdbcTemplate;
    private final MediaWorkerProperties properties;

    public MediaJobStateService(JdbcTemplate jdbcTemplate, MediaWorkerProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public boolean heartbeat(ClaimedMediaJob job) {
        Instant now = Instant.now();
        return jdbcTemplate.update("""
                        update media_processing_jobs
                        set last_heartbeat_at = ?,
                            lease_until = ?
                        where id = ?
                          and generation = ?
                          and worker_id = ?
                          and status = 'PROCESSING'
                        """,
                now,
                now.plus(properties.getLeaseDuration()),
                job.id(),
                job.generation(),
                job.workerId()) == 1;
    }

    public boolean updateStage(ClaimedMediaJob job, MediaProcessingStage stage, int progressPercent) {
        return jdbcTemplate.update("""
                        update media_processing_jobs
                        set stage = ?,
                            progress_percent = ?
                        where id = ?
                          and generation = ?
                          and worker_id = ?
                          and status = 'PROCESSING'
                        """,
                stage.name(),
                progressPercent,
                job.id(),
                job.generation(),
                job.workerId()) == 1;
    }

    public boolean updateProgress(ClaimedMediaJob job, long processedMs, long durationMs, int progressPercent) {
        return jdbcTemplate.update("""
                        update media_processing_jobs
                        set processed_ms = ?,
                            duration_ms = ?,
                            progress_percent = ?
                        where id = ?
                          and generation = ?
                          and worker_id = ?
                          and status = 'PROCESSING'
                        """,
                processedMs,
                durationMs,
                progressPercent,
                job.id(),
                job.generation(),
                job.workerId()) == 1;
    }

    public void failRetryable(ClaimedMediaJob job, String errorCode, String message) {
        var nextRunAt = Instant.now().plus(backoff(job.attempt()));
        jdbcTemplate.update("""
                        update media_processing_jobs
                        set status = case when attempt >= ? then 'FAILED' else 'RETRY_WAIT' end,
                            stage = 'WAITING',
                            worker_id = null,
                            lease_until = null,
                            next_run_at = ?,
                            error_code = ?,
                            error_message = ?
                        where id = ?
                          and generation = ?
                          and worker_id = ?
                          and status = 'PROCESSING'
                        """,
                properties.getMaxAttempts(),
                nextRunAt,
                errorCode,
                abbreviate(message),
                job.id(),
                job.generation(),
                job.workerId());
        if (job.attempt() >= properties.getMaxAttempts()) {
            markVideoFailedIfNoPublishedPackage(job.videoId());
        }
    }

    public void failPermanent(ClaimedMediaJob job, String errorCode, String message) {
        jdbcTemplate.update("""
                        update media_processing_jobs
                        set status = 'FAILED',
                            error_code = ?,
                            error_message = ?,
                            completed_at = current_timestamp(6)
                        where id = ?
                          and generation = ?
                          and worker_id = ?
                          and status = 'PROCESSING'
                        """,
                errorCode,
                abbreviate(message),
                job.id(),
                job.generation(),
                job.workerId());
        markVideoFailedIfNoPublishedPackage(job.videoId());
    }

    public void markVideoFailedIfNoPublishedPackage(Long videoId) {
        jdbcTemplate.update("""
                        update videos
                        set status = 'PROCESSING_FAILED'
                        where id = ?
                          and published_media_package_id is null
                        """,
                videoId);
    }

    public int recoverExpiredLeases() {
        return jdbcTemplate.update("""
                        update media_processing_jobs
                        set status = case when attempt >= ? then 'FAILED' else 'RETRY_WAIT' end,
                            stage = 'WAITING',
                            next_run_at = current_timestamp(6),
                            error_code = 'WORKER_INTERRUPTED',
                            error_message = 'Worker lease expired',
                            worker_id = null,
                            lease_until = null,
                            completed_at = case when attempt >= ? then current_timestamp(6) else completed_at end
                        where status = 'PROCESSING'
                          and lease_until < current_timestamp(6)
                        """,
                properties.getMaxAttempts(),
                properties.getMaxAttempts());
    }

    private Duration backoff(int attempt) {
        return switch (attempt) {
            case 1 -> Duration.ofSeconds(30);
            case 2 -> Duration.ofMinutes(2);
            default -> Duration.ofMinutes(5);
        };
    }

    private String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
