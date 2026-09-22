package com.domain.backend.content.domain;

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
@Table(name = "media_versions")
public class MediaVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long contentId;

    private Long episodeId;

    private Long videoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MediaVersionType versionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ContentStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected MediaVersion() {
    }

    public MediaVersion(Long contentId, Long episodeId, MediaVersionType versionType, ContentStatus status) {
        this.contentId = contentId;
        this.episodeId = episodeId;
        this.versionType = versionType;
        this.status = status;
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

    public Long getId() { return id; }
    public Long getContentId() { return contentId; }
    public Long getEpisodeId() { return episodeId; }
    public Long getVideoId() { return videoId; }
    public MediaVersionType getVersionType() { return versionType; }
    public ContentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(MediaVersionType versionType, ContentStatus status) {
        this.versionType = versionType;
        this.status = status;
    }

    public void attachVideo(Long videoId) {
        this.videoId = videoId;
    }
}
