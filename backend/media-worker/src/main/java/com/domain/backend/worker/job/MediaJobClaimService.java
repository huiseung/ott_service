package com.domain.backend.worker.job;

import com.domain.backend.worker.config.MediaWorkerProperties;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaJobClaimService {

    private final JdbcTemplate jdbcTemplate;
    private final MediaWorkerProperties properties;
    private final WorkerIdentity workerIdentity;

    public MediaJobClaimService(JdbcTemplate jdbcTemplate, MediaWorkerProperties properties, WorkerIdentity workerIdentity) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.workerIdentity = workerIdentity;
    }

    @Transactional
    public Optional<ClaimedMediaJob> claimNext() {
        Long jobId = jdbcTemplate.query("""
                        select id
                        from media_processing_jobs
                        where status = 'QUEUED'
                           or (status = 'RETRY_WAIT' and next_run_at <= current_timestamp(6))
                        order by created_at
                        limit 1
                        for update skip locked
                        """,
                rs -> rs.next() ? rs.getLong("id") : null);
        if (jobId == null) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        jdbcTemplate.update("""
                        update media_processing_jobs
                        set status = 'PROCESSING',
                            stage = 'DOWNLOADING_SOURCE',
                            attempt = attempt + 1,
                            generation = generation + 1,
                            worker_id = ?,
                            lease_until = ?,
                            last_heartbeat_at = ?,
                            started_at = coalesce(started_at, ?),
                            error_code = null,
                            error_message = null
                        where id = ?
                        """,
                workerIdentity.value(),
                now.plus(properties.getLeaseDuration()),
                now,
                now,
                jobId);
        return Optional.ofNullable(jdbcTemplate.queryForObject("""
                        select id, video_id, video_file_id, profile_version, attempt, generation, worker_id
                        from media_processing_jobs
                        where id = ?
                        """,
                this::mapClaimedJob,
                jobId));
    }

    private ClaimedMediaJob mapClaimedJob(ResultSet rs, int rowNum) throws SQLException {
        return new ClaimedMediaJob(
                rs.getLong("id"),
                rs.getLong("video_id"),
                rs.getLong("video_file_id"),
                rs.getString("profile_version"),
                rs.getInt("attempt"),
                rs.getInt("generation"),
                rs.getString("worker_id")
        );
    }
}
