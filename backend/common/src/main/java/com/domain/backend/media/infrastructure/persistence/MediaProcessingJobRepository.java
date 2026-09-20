package com.domain.backend.media.infrastructure.persistence;

import com.domain.backend.media.domain.MediaProcessingJob;
import com.domain.backend.media.domain.MediaProcessingJobStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MediaProcessingJobRepository extends JpaRepository<MediaProcessingJob, Long> {

    Optional<MediaProcessingJob> findByJobKey(String jobKey);

    Optional<MediaProcessingJob> findTopByVideoIdOrderByCreatedAtDesc(Long videoId);

    @Query("""
            select job
            from MediaProcessingJob job
            where (:status is null or job.status = :status)
              and (:videoId is null or job.videoId = :videoId)
            """)
    Page<MediaProcessingJob> search(
            @Param("status") MediaProcessingJobStatus status,
            @Param("videoId") Long videoId,
            Pageable pageable
    );

    long countByJobKey(String jobKey);

    @Modifying
    @Transactional
    @Query("""
            update MediaProcessingJob job
            set job.status = :toStatus,
                job.stage = com.domain.backend.media.domain.MediaProcessingStage.WAITING,
                job.nextRunAt = :nextRunAt,
                job.workerId = null,
                job.leaseUntil = null
            where job.id = :jobId
              and job.status = :fromStatus
            """)
    int transitionToRetryWait(Long jobId, MediaProcessingJobStatus fromStatus, MediaProcessingJobStatus toStatus, Instant nextRunAt);

    @Modifying
    @Transactional
    @Query(value = """
            update media_processing_jobs
            set status = 'QUEUED',
                stage = 'WAITING',
                progress_percent = 0,
                processed_ms = null,
                worker_id = null,
                lease_until = null,
                last_heartbeat_at = null,
                next_run_at = :nextRunAt,
                error_code = null,
                error_message = null
            where id = :jobId
              and status = 'FAILED'
              and error_code not in ('INVALID_MEDIA', 'UNSUPPORTED_VIDEO_CODEC', 'UNSUPPORTED_AUDIO_CODEC',
                                     'RESOLUTION_NOT_SUPPORTED', 'FPS_NOT_SUPPORTED', 'DURATION_NOT_SUPPORTED')
            """, nativeQuery = true)
    int retryFailedJob(Long jobId, Instant nextRunAt);
}
