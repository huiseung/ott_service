package com.domain.backend.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "media_processing_jobs")
public class MediaProcessingJob {

    public static final String DEFAULT_PROFILE_VERSION = "hls-720p-v1";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String jobKey;

    @Column(nullable = false)
    private Long videoId;

    @Column(nullable = false)
    private Long videoFileId;

    @Column(nullable = false, length = 80)
    private String profileVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MediaProcessingJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MediaProcessingStage stage;

    @Column(nullable = false)
    private int progressPercent;

    private Long processedMs;

    private Long durationMs;

    @Column(nullable = false)
    private int attempt;

    @Column(nullable = false)
    private int generation;

    @Column(length = 200)
    private String workerId;

    private Instant leaseUntil;

    private Instant lastHeartbeatAt;

    @Column(nullable = false)
    private Instant nextRunAt;

    @Column(length = 100)
    private String errorCode;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected MediaProcessingJob() {
    }

    public MediaProcessingJob(Long videoId, Long videoFileId, String profileVersion) {
        this.jobKey = jobKey(videoFileId, profileVersion);
        this.videoId = videoId;
        this.videoFileId = videoFileId;
        this.profileVersion = profileVersion;
        this.status = MediaProcessingJobStatus.QUEUED;
        this.stage = MediaProcessingStage.WAITING;
        this.progressPercent = 0;
        this.attempt = 0;
        this.generation = 0;
        this.nextRunAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public static String jobKey(Long videoFileId, String profileVersion) {
        return "HLS:%d:%s".formatted(videoFileId, profileVersion);
    }

    public Long getId() {
        return id;
    }

    public String getJobKey() {
        return jobKey;
    }

    public Long getVideoId() {
        return videoId;
    }

    public Long getVideoFileId() {
        return videoFileId;
    }

    public String getProfileVersion() {
        return profileVersion;
    }

    public MediaProcessingJobStatus getStatus() {
        return status;
    }

    public MediaProcessingStage getStage() {
        return stage;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public Long getProcessedMs() {
        return processedMs;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public int getAttempt() {
        return attempt;
    }

    public int getGeneration() {
        return generation;
    }

    public String getWorkerId() {
        return workerId;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void failPermanently(String errorCode, String message) {
        this.status = MediaProcessingJobStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = message;
        this.completedAt = Instant.now();
    }
}
