package com.domain.backend.collection.presentation;

import com.domain.backend.collection.application.AdminCollectionDtos.AdminCollectionResponse;
import com.domain.backend.collection.application.AdminCollectionDtos.CollectionItemsRequest;
import com.domain.backend.collection.application.AdminCollectionDtos.CollectionRequest;
import com.domain.backend.collection.application.AdminCollectionDtos.CollectionUpdateRequest;
import com.domain.backend.collection.application.AdminCollectionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/video-collections")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCollectionController {

    private final AdminCollectionService collectionService;

    public AdminCollectionController(AdminCollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @PostMapping
    public ResponseEntity<AdminCollectionResponse> create(@Valid @RequestBody CollectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(collectionService.create(request));
    }

    @GetMapping
    public List<AdminCollectionResponse> list() {
        return collectionService.list();
    }

    @GetMapping("/{collectionId}")
    public AdminCollectionResponse get(@PathVariable Long collectionId) {
        return collectionService.get(collectionId);
    }

    @PutMapping("/{collectionId}")
    public AdminCollectionResponse update(
            @PathVariable Long collectionId,
            @Valid @RequestBody CollectionUpdateRequest request
    ) {
        return collectionService.update(collectionId, request);
    }

    @DeleteMapping("/{collectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long collectionId) {
        collectionService.delete(collectionId);
    }

    @PutMapping("/{collectionId}/items")
    public AdminCollectionResponse replaceItems(
            @PathVariable Long collectionId,
            @Valid @RequestBody CollectionItemsRequest request
    ) {
        return collectionService.replaceItems(collectionId, request);
    }
}
