package com.domain.backend.media.infrastructure.persistence;

import com.domain.backend.media.domain.MediaPackage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaPackageRepository extends JpaRepository<MediaPackage, Long> {
}
