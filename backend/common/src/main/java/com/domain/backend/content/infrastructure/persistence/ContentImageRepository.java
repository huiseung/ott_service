package com.domain.backend.content.infrastructure.persistence;

import com.domain.backend.content.domain.ContentImage;
import com.domain.backend.content.domain.ContentImageType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentImageRepository extends JpaRepository<ContentImage, Long> {

    Optional<ContentImage> findByContentIdAndImageType(Long contentId, ContentImageType imageType);

    List<ContentImage> findByContentIdOrderByImageTypeAsc(Long contentId);

    List<ContentImage> findByContentIdInAndImageType(Collection<Long> contentIds, ContentImageType imageType);

    void deleteByContentIdAndImageType(Long contentId, ContentImageType imageType);
}
