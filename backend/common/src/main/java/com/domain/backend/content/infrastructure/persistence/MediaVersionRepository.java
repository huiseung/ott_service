package com.domain.backend.content.infrastructure.persistence;

import com.domain.backend.content.domain.MediaVersion;
import com.domain.backend.content.domain.MediaVersionType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaVersionRepository extends JpaRepository<MediaVersion, Long> {

    boolean existsByContentIdAndVersionType(Long contentId, MediaVersionType versionType);

    boolean existsByEpisodeIdAndVersionType(Long episodeId, MediaVersionType versionType);

    boolean existsByVideoId(Long videoId);

    Optional<MediaVersion> findByVideoId(Long videoId);

    List<MediaVersion> findByContentIdOrderByIdAsc(Long contentId);

    List<MediaVersion> findByEpisodeIdOrderByIdAsc(Long episodeId);
}
