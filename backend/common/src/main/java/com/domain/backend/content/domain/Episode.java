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
@Table(name = "episodes")
public class Episode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long seasonId;

    @Column(nullable = false)
    private int episodeNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ContentStatus status;

    private Instant releaseAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Episode() {
    }

    public Episode(Long seasonId, int episodeNumber, ContentStatus status, Instant releaseAt) {
        this.seasonId = seasonId;
        this.episodeNumber = episodeNumber;
        this.status = status;
        this.releaseAt = releaseAt;
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
    public Long getSeasonId() { return seasonId; }
    public int getEpisodeNumber() { return episodeNumber; }
    public ContentStatus getStatus() { return status; }
    public Instant getReleaseAt() { return releaseAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(int episodeNumber, ContentStatus status, Instant releaseAt) {
        this.episodeNumber = episodeNumber;
        this.status = status;
        this.releaseAt = releaseAt;
    }
}
