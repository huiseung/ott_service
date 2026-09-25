package com.domain.backend.collection.application;

import com.domain.backend.collection.application.PublicCollectionDtos.CollectionListResponse;
import com.domain.backend.collection.application.PublicCollectionDtos.CollectionResponse;
import com.domain.backend.collection.application.PublicCollectionDtos.CollectionVideoItem;
import com.domain.backend.collection.domain.VideoCollectionType;
import com.domain.backend.user.application.ContentEligibility;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicCollectionQueryService {

    private final ContentEligibility eligibility;
    private final JdbcTemplate jdbcTemplate;

    public PublicCollectionQueryService(JdbcTemplate jdbcTemplate, ContentEligibility eligibility) {
        this.eligibility = eligibility;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public CollectionListResponse listCollections() {
        List<CollectionResponse> collections = jdbcTemplate.query("""
                        select id, collection_key, title, subtitle, collection_type, reference_value, item_limit
                        from video_collections
                        where enabled = true
                        order by display_order asc, id asc
                        """,
                (rs, rowNum) -> {
                    Long collectionId = rs.getLong("id");
                    return new CollectionResponse(
                            collectionId,
                            rs.getString("collection_key"),
                            rs.getString("title"),
                            rs.getString("subtitle"),
                            VideoCollectionType.valueOf(rs.getString("collection_type")),
                            rs.getString("reference_value"),
                            listCollectionVideos(collectionId, rs.getInt("item_limit"))
                    );
                });
        return new CollectionListResponse(collections);
    }

    @Transactional(readOnly = true)
    public List<CollectionVideoItem> listCollectionVideos(Long collectionId, Integer size) {
        int limit = Math.max(1, Math.min(size == null ? 20 : size, 100));
        Object[] window = eligibility.parameters();
        return jdbcTemplate.query("""
                        select v.id, v.title, p.duration_ms
                        from video_collection_items i
                        join videos v on v.id = i.video_id
                        join media_packages p on p.id = v.published_media_package_id
                        join video_collections c on c.id = i.collection_id
                        """ + ContentEligibility.JOINS + " where " + ContentEligibility.PREDICATE + """
                          and i.collection_id = ?
                          and c.enabled = true
                          and v.status = 'READY'
                          and p.status = 'READY'
                          and v.published_media_package_id is not null
                        order by i.sort_order asc, v.id desc
                        limit ?
                        """,
                (rs, rowNum) -> new CollectionVideoItem(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getLong("duration_ms")
                ),
                window[0], window[1], window[2], window[3], collectionId,
                limit);
    }
}
