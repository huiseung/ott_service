package com.domain.backend.video.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "upload_idempotency_keys")
public class UploadIdempotencyKey {

    @Id
    @Column(name = "idempotency_key", length = 200)
    private String key;

    @Column(nullable = false, length = 128)
    private String requestHash;

    @Column(nullable = false)
    private Long videoId;

    @Column(nullable = false)
    private Long videoFileId;

    @Column(nullable = false)
    private Instant createdAt;

    protected UploadIdempotencyKey() {
    }

    public UploadIdempotencyKey(String key, String requestHash, Long videoId, Long videoFileId) {
        this.key = key;
        this.requestHash = requestHash;
        this.videoId = videoId;
        this.videoFileId = videoFileId;
        this.createdAt = Instant.now();
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Long getVideoId() {
        return videoId;
    }

    public Long getVideoFileId() {
        return videoFileId;
    }
}
