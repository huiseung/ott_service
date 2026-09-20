package com.domain.backend.video.infrastructure.persistence;

import com.domain.backend.video.domain.VideoFile;
import com.domain.backend.video.domain.VideoFileStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface VideoFileRepository extends JpaRepository<VideoFile, Long> {

    @Query("select coalesce(max(vf.generation), 0) from VideoFile vf where vf.video.id = :videoId")
    int findMaxGeneration(Long videoId);

    @Modifying
    @Transactional
    @Query("update VideoFile vf set vf.status = :toStatus where vf.id = :id and vf.status = :fromStatus")
    int updateStatus(Long id, VideoFileStatus fromStatus, VideoFileStatus toStatus);

    @Modifying
    @Transactional
    @Query("update VideoFile vf set vf.status = :status where vf.id = :id")
    int forceStatus(Long id, VideoFileStatus status);

    List<VideoFile> findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(VideoFileStatus status, Instant before);

    List<VideoFile> findTop100ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            Collection<VideoFileStatus> statuses,
            Instant before
    );
}
