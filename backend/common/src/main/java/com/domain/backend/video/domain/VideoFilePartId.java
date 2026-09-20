package com.domain.backend.video.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class VideoFilePartId implements Serializable {

    @Column(name = "video_file_id")
    private Long videoFileId;

    @Column(name = "part_number")
    private Integer partNumber;

    protected VideoFilePartId() {
    }

    public VideoFilePartId(Long videoFileId, Integer partNumber) {
        this.videoFileId = videoFileId;
        this.partNumber = partNumber;
    }

    public Long getVideoFileId() {
        return videoFileId;
    }

    public Integer getPartNumber() {
        return partNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VideoFilePartId that)) {
            return false;
        }
        return Objects.equals(videoFileId, that.videoFileId) && Objects.equals(partNumber, that.partNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(videoFileId, partNumber);
    }
}
