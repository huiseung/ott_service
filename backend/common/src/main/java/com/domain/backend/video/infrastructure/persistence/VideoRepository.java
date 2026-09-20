package com.domain.backend.video.infrastructure.persistence;

import com.domain.backend.video.domain.Video;
import com.domain.backend.video.domain.VideoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, Long> {

    Page<Video> findByStatus(VideoStatus status, Pageable pageable);
}
