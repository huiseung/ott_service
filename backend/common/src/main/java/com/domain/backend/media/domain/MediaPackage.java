package com.domain.backend.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "media_packages")
public class MediaPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long videoId;

    @Column(nullable = false)
    private Long sourceVideoFileId;

    @Column(nullable = false, length = 80)
    private String profileVersion;

    @Column(nullable = false, length = 512)
    private String rootKey;

    @Column(nullable = false, length = 512)
    private String masterManifestKey;

    @Column(nullable = false)
    private long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MediaPackageStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    protected MediaPackage() {
    }

    public MediaPackage(Long videoId, Long sourceVideoFileId, String profileVersion, String rootKey,
                        String masterManifestKey, long durationMs) {
        this.videoId = videoId;
        this.sourceVideoFileId = sourceVideoFileId;
        this.profileVersion = profileVersion;
        this.rootKey = rootKey;
        this.masterManifestKey = masterManifestKey;
        this.durationMs = durationMs;
        this.status = MediaPackageStatus.READY;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getProfileVersion() {
        return profileVersion;
    }

    public Long getVideoId() {
        return videoId;
    }

    public Long getSourceVideoFileId() {
        return sourceVideoFileId;
    }

    public String getRootKey() {
        return rootKey;
    }

    public String getMasterManifestKey() {
        return masterManifestKey;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public MediaPackageStatus getStatus() {
        return status;
    }
}
