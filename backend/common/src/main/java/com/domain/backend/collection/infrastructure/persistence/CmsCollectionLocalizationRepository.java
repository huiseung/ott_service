package com.domain.backend.collection.infrastructure.persistence;

import com.domain.backend.collection.domain.CmsCollectionLocalization;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CmsCollectionLocalizationRepository extends JpaRepository<CmsCollectionLocalization, Long> {

    boolean existsByCollectionIdAndLocale(Long collectionId, String locale);

    Optional<CmsCollectionLocalization> findByCollectionIdAndLocale(Long collectionId, String locale);

    List<CmsCollectionLocalization> findByCollectionIdOrderByLocaleAsc(Long collectionId);

    void deleteByCollectionIdAndLocale(Long collectionId, String locale);
}
