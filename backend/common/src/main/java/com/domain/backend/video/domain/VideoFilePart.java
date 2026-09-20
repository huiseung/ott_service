package com.domain.backend.video.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "video_file_parts")
public class VideoFilePart {

    @EmbeddedId
    private VideoFilePartId id;

    @Column(nullable = false, length = 512)
    private String etag;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    private Instant uploadedAt;

    protected VideoFilePart() {
    }

    public VideoFilePart(VideoFilePartId id, String etag, long size, Instant uploadedAt) {
        this.id = id;
        this.etag = etag;
        this.size = size;
        this.uploadedAt = uploadedAt;
    }

    public VideoFilePartId getId() {
        return id;
    }

    public String getEtag() {
        return etag;
    }

    public long getSize() {
        return size;
    }
}
