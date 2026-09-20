package com.domain.backend.media.infrastructure.persistence;

import com.domain.backend.media.domain.MediaRendition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaRenditionRepository extends JpaRepository<MediaRendition, Long> {

    List<MediaRendition> findByMediaPackageIdOrderByNameAsc(Long mediaPackageId);
}
