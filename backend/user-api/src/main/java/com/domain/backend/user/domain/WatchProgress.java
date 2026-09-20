package com.domain.backend.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "watch_progress")
public class WatchProgress {

    @EmbeddedId
    private WatchProgressId id;

    @Column(nullable = false)
    private Long mediaPackageId;

    @Column(nullable = false)
    private Long playbackSessionId;

    @Column(nullable = false)
    private long positionMs;

    @Column(nullable = false)
    private long durationMs;

    @Column(nullable = false)
    private long clientEventSeq;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected WatchProgress() {
    }

    public WatchProgress(WatchProgressId id, Long mediaPackageId, Long playbackSessionId, long positionMs,
                         long durationMs, long clientEventSeq, Instant occurredAt) {
        this.id = id;
        this.mediaPackageId = mediaPackageId;
        this.playbackSessionId = playbackSessionId;
        this.positionMs = positionMs;
        this.durationMs = durationMs;
        this.clientEventSeq = clientEventSeq;
        this.occurredAt = occurredAt;
        this.updatedAt = Instant.now();
    }

    public WatchProgressId getId() { return id; }
    public long getPositionMs() { return positionMs; }
    public long getDurationMs() { return durationMs; }
    public long getClientEventSeq() { return clientEventSeq; }
    public Long getPlaybackSessionId() { return playbackSessionId; }
    public Instant getOccurredAt() { return occurredAt; }
}
