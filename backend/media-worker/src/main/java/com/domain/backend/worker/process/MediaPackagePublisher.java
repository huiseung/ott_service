package com.domain.backend.worker.process;

import com.domain.backend.media.domain.MediaProcessingStage;
import com.domain.backend.worker.config.MediaProfileProperties;
import com.domain.backend.worker.job.ClaimedMediaJob;
import com.domain.backend.worker.job.MediaJobStateService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaPackagePublisher {

    private final JdbcTemplate jdbcTemplate;
    private final MediaProfileProperties profile;
    private final MediaJobStateService stateService;

    public MediaPackagePublisher(JdbcTemplate jdbcTemplate, MediaProfileProperties profile,
                                 MediaJobStateService stateService) {
        this.jdbcTemplate = jdbcTemplate;
        this.profile = profile;
        this.stateService = stateService;
    }

    @Transactional
    public PublishOutcome publish(ClaimedMediaJob job, PackageUploadResult uploadResult, MediaProbeResult probeResult) {
        stateService.updateStage(job, MediaProcessingStage.PUBLISHING, 98);
        var rows = jdbcTemplate.queryForList("""
                        select j.id as job_id, v.active_video_file_id
                        from media_processing_jobs j
                        join videos v on v.id = j.video_id
                        where j.id = ?
                          and j.generation = ?
                          and j.worker_id = ?
                          and j.status = 'PROCESSING'
                        for update
                        """,
                job.id(),
                job.generation(),
                job.workerId());
        if (rows.isEmpty()) {
            return PublishOutcome.STALE_WORKER;
        }
        Long activeVideoFileId = ((Number) rows.getFirst().get("active_video_file_id")).longValue();
        if (!activeVideoFileId.equals(job.videoFileId())) {
            jdbcTemplate.update("""
                            update media_processing_jobs
                            set status = 'SUPERSEDED',
                                error_code = 'SOURCE_SUPERSEDED',
                                error_message = 'Source VideoFile is no longer active',
                                completed_at = ?
                            where id = ?
                            """,
                    Instant.now(),
                    job.id());
            return PublishOutcome.SUPERSEDED;
        }

        Long packageId = insertPackage(job, uploadResult, probeResult);
        insertRendition(packageId, uploadResult, probeResult);
        jdbcTemplate.update("""
                        update videos
                        set status = 'READY',
                            published_media_package_id = ?,
                            updated_at = ?
                        where id = ?
                          and active_video_file_id = ?
                        """,
                packageId,
                Instant.now(),
                job.videoId(),
                job.videoFileId());
        jdbcTemplate.update("""
                        update media_processing_jobs
                        set status = 'COMPLETED',
                            stage = 'COMPLETED',
                            progress_percent = 100,
                            processed_ms = ?,
                            duration_ms = ?,
                            completed_at = ?,
                            lease_until = null
                        where id = ?
                          and generation = ?
                          and worker_id = ?
                          and status = 'PROCESSING'
                        """,
                probeResult.durationMs(),
                probeResult.durationMs(),
                Instant.now(),
                job.id(),
                job.generation(),
                job.workerId());
        return PublishOutcome.PUBLISHED;
    }

    private Long insertPackage(ClaimedMediaJob job, PackageUploadResult uploadResult, MediaProbeResult probeResult) {
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                            insert into media_packages(video_id, source_video_file_id, profile_version, root_key,
                                                       master_manifest_key, duration_ms, status, created_at)
                            values (?, ?, ?, ?, ?, ?, 'READY', ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, job.videoId());
            ps.setLong(2, job.videoFileId());
            ps.setString(3, job.profileVersion());
            ps.setString(4, uploadResult.rootKey());
            ps.setString(5, uploadResult.masterManifestKey());
            ps.setLong(6, probeResult.durationMs());
            ps.setObject(7, Instant.now());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void insertRendition(Long packageId, PackageUploadResult uploadResult, MediaProbeResult probeResult) {
        jdbcTemplate.update("""
                        insert into media_renditions(media_package_id, name, width, height, video_codec, audio_codec,
                                                     video_bitrate, audio_bitrate, playlist_key)
                        values (?, ?, ?, ?, 'h264', 'aac', null, 128000, ?)
                        """,
                packageId,
                profile.getRenditionName(),
                Math.min(profile.getMaxWidth(), probeResult.width()),
                Math.min(profile.getMaxHeight(), probeResult.height()),
                uploadResult.playlistKey());
    }

    public enum PublishOutcome {
        PUBLISHED,
        STALE_WORKER,
        SUPERSEDED
    }
}
