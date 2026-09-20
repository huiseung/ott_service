package com.domain.backend.collection.application;

import com.domain.backend.collection.application.AdminCollectionDtos.AdminCollectionResponse;
import com.domain.backend.collection.application.AdminCollectionDtos.AdminCollectionVideoItem;
import com.domain.backend.collection.application.AdminCollectionDtos.CollectionItemsRequest;
import com.domain.backend.collection.application.AdminCollectionDtos.CollectionRequest;
import com.domain.backend.collection.application.AdminCollectionDtos.CollectionUpdateRequest;
import com.domain.backend.collection.domain.VideoCollection;
import com.domain.backend.collection.infrastructure.persistence.VideoCollectionRepository;
import com.domain.backend.video.infrastructure.persistence.VideoRepository;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminCollectionService {

    private final VideoCollectionRepository collectionRepository;
    private final VideoRepository videoRepository;
    private final JdbcTemplate jdbcTemplate;

    public AdminCollectionService(VideoCollectionRepository collectionRepository, VideoRepository videoRepository,
                                  JdbcTemplate jdbcTemplate) {
        this.collectionRepository = collectionRepository;
        this.videoRepository = videoRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public AdminCollectionResponse create(CollectionRequest request) {
        if (collectionRepository.existsByCollectionKey(request.collectionKey())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Collection key already exists");
        }
        var collection = collectionRepository.save(new VideoCollection(
                request.collectionKey(),
                request.title(),
                request.subtitle(),
                request.collectionType(),
                request.referenceValue(),
                request.itemLimit(),
                request.displayOrder(),
                request.enabled()
        ));
        return response(collection);
    }

    @Transactional(readOnly = true)
    public List<AdminCollectionResponse> list() {
        return collectionRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminCollectionResponse get(Long collectionId) {
        return response(requireCollection(collectionId));
    }

    @Transactional
    public AdminCollectionResponse update(Long collectionId, CollectionUpdateRequest request) {
        var collection = requireCollection(collectionId);
        collection.update(
                request.title(),
                request.subtitle(),
                request.collectionType(),
                request.referenceValue(),
                request.itemLimit(),
                request.displayOrder(),
                request.enabled()
        );
        return response(collection);
    }

    @Transactional
    public void delete(Long collectionId) {
        collectionRepository.delete(requireCollection(collectionId));
    }

    @Transactional
    public AdminCollectionResponse replaceItems(Long collectionId, CollectionItemsRequest request) {
        var collection = requireCollection(collectionId);
        Set<Long> seenVideoIds = new HashSet<>();
        for (Long videoId : request.videoIds()) {
            if (!seenVideoIds.add(videoId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate video id: " + videoId);
            }
            if (!videoRepository.existsById(videoId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video does not exist: " + videoId);
            }
        }
        jdbcTemplate.update("delete from video_collection_items where collection_id = ?", collectionId);
        Instant now = Instant.now();
        jdbcTemplate.batchUpdate("""
                        insert into video_collection_items(collection_id, video_id, sort_order, added_at)
                        values (?, ?, ?, ?)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                        ps.setLong(1, collectionId);
                        ps.setLong(2, request.videoIds().get(i));
                        ps.setInt(3, i + 1);
                        ps.setTimestamp(4, Timestamp.from(now));
                    }

                    @Override
                    public int getBatchSize() {
                        return request.videoIds().size();
                    }
                });
        return response(collection);
    }

    private VideoCollection requireCollection(Long collectionId) {
        return collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection not found"));
    }

    private AdminCollectionResponse response(VideoCollection collection) {
        List<AdminCollectionVideoItem> items = jdbcTemplate.query("""
                        select i.video_id, v.title, i.sort_order
                        from video_collection_items i
                        join videos v on v.id = i.video_id
                        where i.collection_id = ?
                        order by i.sort_order asc, i.video_id asc
                        """,
                (rs, rowNum) -> new AdminCollectionVideoItem(
                        rs.getLong("video_id"),
                        rs.getString("title"),
                        rs.getInt("sort_order")
                ),
                collection.getId());
        return new AdminCollectionResponse(
                collection.getId(),
                collection.getCollectionKey(),
                collection.getTitle(),
                collection.getSubtitle(),
                collection.getCollectionType(),
                collection.getReferenceValue(),
                collection.getItemLimit(),
                collection.getDisplayOrder(),
                collection.isEnabled(),
                collection.getCreatedAt(),
                collection.getUpdatedAt(),
                items
        );
    }
}
