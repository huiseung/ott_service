package com.domain.backend.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "media_renditions",
        uniqueConstraints = @UniqueConstraint(name = "uk_media_renditions_package_name", columnNames = {"media_package_id", "name"})
)
public class MediaRendition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long mediaPackageId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(nullable = false, length = 80)
    private String videoCodec;

    @Column(nullable = false, length = 80)
    private String audioCodec;

    private Integer videoBitrate;

    private Integer audioBitrate;

    @Column(nullable = false, length = 512)
    private String playlistKey;

    protected MediaRendition() {
    }

    public MediaRendition(Long mediaPackageId, String name, int width, int height, String videoCodec,
                          String audioCodec, Integer videoBitrate, Integer audioBitrate, String playlistKey) {
        this.mediaPackageId = mediaPackageId;
        this.name = name;
        this.width = width;
        this.height = height;
        this.videoCodec = videoCodec;
        this.audioCodec = audioCodec;
        this.videoBitrate = videoBitrate;
        this.audioBitrate = audioBitrate;
        this.playlistKey = playlistKey;
    }

    public Long getId() {
        return id;
    }

    public Long getMediaPackageId() {
        return mediaPackageId;
    }

    public String getName() {
        return name;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getVideoCodec() {
        return videoCodec;
    }

    public String getAudioCodec() {
        return audioCodec;
    }

    public Integer getVideoBitrate() {
        return videoBitrate;
    }

    public Integer getAudioBitrate() {
        return audioBitrate;
    }

    public String getPlaylistKey() {
        return playlistKey;
    }
}
