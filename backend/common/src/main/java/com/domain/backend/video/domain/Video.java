package com.domain.backend.video.domain;

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
@Table(name = "videos")
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private VideoStatus status;

    private Long activeVideoFileId;

    private Long publishedMediaPackageId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Video() {
    }

    public Video(String title) {
        this.title = title;
        this.status = VideoStatus.DRAFT;
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

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public VideoStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getActiveVideoFileId() {
        return activeVideoFileId;
    }

    public Long getPublishedMediaPackageId() {
        return publishedMediaPackageId;
    }

    public void markProcessing(Long activeVideoFileId) {
        this.activeVideoFileId = activeVideoFileId;
        this.status = VideoStatus.PROCESSING;
    }

    public void publish(Long activeVideoFileId, Long mediaPackageId) {
        this.activeVideoFileId = activeVideoFileId;
        this.publishedMediaPackageId = mediaPackageId;
        this.status = VideoStatus.READY;
    }

    public void markProcessingFailed() {
        if (this.publishedMediaPackageId == null) {
            this.status = VideoStatus.PROCESSING_FAILED;
        }
    }
}
