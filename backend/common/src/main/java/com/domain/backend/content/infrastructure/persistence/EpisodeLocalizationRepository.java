package com.domain.backend.content.infrastructure.persistence;

import com.domain.backend.content.domain.EpisodeLocalization;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeLocalizationRepository extends JpaRepository<EpisodeLocalization, Long> {

    boolean existsByEpisodeIdAndLocale(Long episodeId, String locale);

    Optional<EpisodeLocalization> findByEpisodeIdAndLocale(Long episodeId, String locale);

    List<EpisodeLocalization> findByEpisodeIdOrderByLocaleAsc(Long episodeId);

    List<EpisodeLocalization> findByEpisodeIdInOrderByEpisodeIdAscLocaleAsc(Collection<Long> episodeIds);

    void deleteByEpisodeIdAndLocale(Long episodeId, String locale);
}
