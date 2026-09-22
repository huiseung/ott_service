package com.domain.backend.content.infrastructure.persistence;

import com.domain.backend.content.domain.ContentLocalization;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentLocalizationRepository extends JpaRepository<ContentLocalization, Long> {

    boolean existsByContentIdAndLocale(Long contentId, String locale);

    Optional<ContentLocalization> findByContentIdAndLocale(Long contentId, String locale);

    List<ContentLocalization> findByContentIdOrderByLocaleAsc(Long contentId);

    void deleteByContentIdAndLocale(Long contentId, String locale);
}
