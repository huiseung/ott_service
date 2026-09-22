package com.domain.backend.content.infrastructure.persistence;

import com.domain.backend.content.domain.EpisodeImage;
import com.domain.backend.content.domain.EpisodeImageType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeImageRepository extends JpaRepository<EpisodeImage, Long> {

    Optional<EpisodeImage> findByEpisodeIdAndImageType(Long episodeId, EpisodeImageType imageType);

    List<EpisodeImage> findByEpisodeIdOrderByImageTypeAsc(Long episodeId);

    void deleteByEpisodeIdAndImageType(Long episodeId, EpisodeImageType imageType);
}
