package com.domain.backend.collection.infrastructure.persistence;

import com.domain.backend.collection.domain.CmsCollectionItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CmsCollectionItemRepository extends JpaRepository<CmsCollectionItem, Long> {

    boolean existsByCollectionIdAndContentId(Long collectionId, Long contentId);

    Optional<CmsCollectionItem> findByCollectionIdAndContentId(Long collectionId, Long contentId);

    List<CmsCollectionItem> findByCollectionIdOrderByDisplayOrderAsc(Long collectionId);

    List<CmsCollectionItem> findByCollectionIdAndContentIdIn(Long collectionId, Collection<Long> contentIds);

    long countByCollectionId(Long collectionId);

    void deleteByCollectionIdAndContentId(Long collectionId, Long contentId);
}
