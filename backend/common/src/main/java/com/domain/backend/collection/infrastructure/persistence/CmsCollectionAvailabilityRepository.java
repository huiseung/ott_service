package com.domain.backend.collection.infrastructure.persistence;

import com.domain.backend.collection.domain.CmsCollectionAvailability;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CmsCollectionAvailabilityRepository extends JpaRepository<CmsCollectionAvailability, Long> {

    Optional<CmsCollectionAvailability> findByCollectionIdAndCountryCode(Long collectionId, String countryCode);

    List<CmsCollectionAvailability> findByCollectionIdOrderByCountryCodeAsc(Long collectionId);

    void deleteByCollectionIdAndCountryCode(Long collectionId, String countryCode);
}
