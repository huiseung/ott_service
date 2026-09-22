package com.domain.backend.content.infrastructure.persistence;

import com.domain.backend.content.domain.Episode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {

    boolean existsBySeasonIdAndEpisodeNumber(Long seasonId, int episodeNumber);

    List<Episode> findBySeasonIdOrderByEpisodeNumberAsc(Long seasonId);
}
