package com.domain.backend.video.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "video_files",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_video_file_generation", columnNames = {"video_id", "generation"}),
                @UniqueConstraint(name = "uk_video_file_object_key", columnNames = "object_key")
        }
)
public class VideoFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Column(nullable = false)
    private int generation;

    @Column(nullable = false, length = 500)
    private String originalFilename;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false)
    private String fingerprint;

    @Column(nullable = false, length = 512)
    private String objectKey;

    @Column(length = 1024)
    private String multipartUploadId;

    @Column(nullable = false)
    private long partSize;

    @Column(nullable = false)
    private int totalParts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private VideoFileStatus status;

    @Column(length = 128)
    private String checksumSha256;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant completedAt;

    protected VideoFile() {
    }

    public VideoFile(Video video, int generation, String originalFilename, String contentType, long fileSize,
                     String fingerprint, String objectKey, long partSize, int totalParts) {
        this.video = video;
        this.generation = generation;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.fingerprint = fingerprint;
        this.objectKey = objectKey;
        this.partSize = partSize;
        this.totalParts = totalParts;
        this.status = VideoFileStatus.PREPARING;
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

    public void markUploading(String multipartUploadId) {
        this.multipartUploadId = multipartUploadId;
        this.status = VideoFileStatus.UPLOADING;
    }

    public void markCompleted() {
        this.status = VideoFileStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void markAborted() {
        this.status = VideoFileStatus.ABORTED;
    }

    public void markFailed() {
        this.status = VideoFileStatus.FAILED;
    }

    public Long getId() {
        return id;
    }

    public Video getVideo() {
        return video;
    }

    public int getGeneration() {
        return generation;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getMultipartUploadId() {
        return multipartUploadId;
    }

    public long getPartSize() {
        return partSize;
    }

    public int getTotalParts() {
        return totalParts;
    }

    public VideoFileStatus getStatus() {
        return status;
    }
}
