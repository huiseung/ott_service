package com.domain.backend.collection.presentation;

import com.domain.backend.collection.application.PublicCollectionDtos.CollectionListResponse;
import com.domain.backend.collection.application.PublicCollectionDtos.CollectionVideoItem;
import com.domain.backend.collection.application.PublicCollectionQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/collections")
public class PublicCollectionController {

    private final PublicCollectionQueryService queryService;

    public PublicCollectionController(PublicCollectionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public CollectionListResponse listCollections() {
        return queryService.listCollections();
    }

    @GetMapping("/{collectionId}/videos")
    public List<CollectionVideoItem> listCollectionVideos(
            @PathVariable Long collectionId,
            @RequestParam(required = false) Integer size
    ) {
        return queryService.listCollectionVideos(collectionId, size);
    }
}
