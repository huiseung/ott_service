package com.domain.backend.video.infrastructure.persistence;

import com.domain.backend.video.domain.VideoFilePart;
import com.domain.backend.video.domain.VideoFilePartId;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface VideoFilePartRepository extends JpaRepository<VideoFilePart, VideoFilePartId> {

    List<VideoFilePart> findByIdVideoFileIdOrderByIdPartNumberAsc(Long videoFileId);

    long countByIdVideoFileId(Long videoFileId);

    @Modifying
    @Transactional
    @Query(value = """
            insert into video_file_parts(video_file_id, part_number, etag, size, uploaded_at)
            values (:videoFileId, :partNumber, :etag, :size, :uploadedAt)
            on duplicate key update etag = values(etag),
                                    size = values(size),
                                    uploaded_at = values(uploaded_at)
            """, nativeQuery = true)
    void upsert(Long videoFileId, int partNumber, String etag, long size, Instant uploadedAt);
}
