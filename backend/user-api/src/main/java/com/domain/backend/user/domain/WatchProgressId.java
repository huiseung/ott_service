package com.domain.backend.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class WatchProgressId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "video_id")
    private Long videoId;

    protected WatchProgressId() {
    }

    public WatchProgressId(Long userId, Long videoId) {
        this.userId = userId;
        this.videoId = videoId;
    }

    public Long getUserId() { return userId; }
    public Long getVideoId() { return videoId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WatchProgressId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(videoId, that.videoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, videoId);
    }
}
