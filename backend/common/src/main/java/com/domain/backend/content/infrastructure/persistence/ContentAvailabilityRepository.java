package com.domain.backend.content.infrastructure.persistence;

import com.domain.backend.content.domain.ContentAvailability;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentAvailabilityRepository extends JpaRepository<ContentAvailability, Long> {

    boolean existsByContentIdAndCountryCode(Long contentId, String countryCode);

    Optional<ContentAvailability> findByContentIdAndCountryCode(Long contentId, String countryCode);

    List<ContentAvailability> findByContentIdOrderByCountryCodeAsc(Long contentId);
}
