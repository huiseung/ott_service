package com.domain.backend.collection.infrastructure.persistence;

import com.domain.backend.collection.domain.CmsCollection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CmsCollectionRepository extends JpaRepository<CmsCollection, Long> {
}
