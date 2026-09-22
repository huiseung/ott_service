package com.domain.backend.content.infrastructure.persistence;

import com.domain.backend.content.domain.Season;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonRepository extends JpaRepository<Season, Long> {

    boolean existsBySeriesContentIdAndSeasonNumber(Long seriesContentId, int seasonNumber);

    List<Season> findBySeriesContentIdOrderBySeasonNumberAsc(Long seriesContentId);
}
