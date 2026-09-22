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
@Table(name = "episode_images")
public class EpisodeImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long episodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EpisodeImageType imageType;

    @Column(nullable = false, length = 512)
    private String objectKey;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false, length = 100)
    private String mimeType;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected EpisodeImage() {
    }

    public EpisodeImage(Long episodeId, EpisodeImageType imageType, String objectKey, int width, int height,
                        long fileSize, String mimeType) {
        this.episodeId = episodeId;
        this.imageType = imageType;
        this.objectKey = objectKey;
        this.width = width;
        this.height = height;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
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
    public Long getEpisodeId() { return episodeId; }
    public EpisodeImageType getImageType() { return imageType; }
    public String getObjectKey() { return objectKey; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getFileSize() { return fileSize; }
    public String getMimeType() { return mimeType; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void replace(String objectKey, int width, int height, long fileSize, String mimeType) {
        this.objectKey = objectKey;
        this.width = width;
        this.height = height;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
    }
}
