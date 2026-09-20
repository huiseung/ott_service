package com.domain.backend.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "playback_sessions")
public class PlaybackSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String sessionToken;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long videoId;

    @Column(nullable = false)
    private Long mediaPackageId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastAccessedAt;

    protected PlaybackSession() {
    }

    public PlaybackSession(String sessionToken, Long userId, Long videoId, Long mediaPackageId, Instant expiresAt) {
        Instant now = Instant.now();
        this.sessionToken = sessionToken;
        this.userId = userId;
        this.videoId = videoId;
        this.mediaPackageId = mediaPackageId;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.lastAccessedAt = now;
    }

    public Long getId() { return id; }
    public String getSessionToken() { return sessionToken; }
    public Long getUserId() { return userId; }
    public Long getVideoId() { return videoId; }
    public Long getMediaPackageId() { return mediaPackageId; }
    public Instant getExpiresAt() { return expiresAt; }
}
