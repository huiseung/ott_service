package com.domain.backend.collection.infrastructure.persistence;

import com.domain.backend.collection.domain.VideoCollection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoCollectionRepository extends JpaRepository<VideoCollection, Long> {

    boolean existsByCollectionKey(String collectionKey);

    Optional<VideoCollection> findByCollectionKey(String collectionKey);

    List<VideoCollection> findAllByOrderByDisplayOrderAscIdAsc();
}
