package com.domain.backend.collection.presentation;

import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionAvailabilityBulkRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionAvailabilityRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionAvailabilityResponse;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionItemsRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionListItem;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionLocalizationRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionLocalizationUpdateRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionPreviewResponse;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionResponse;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionUpdateRequest;
import com.domain.backend.collection.application.AdminCmsCollectionService;
import com.domain.backend.content.domain.ContentStatus;
import com.domain.backend.media.application.AdminMediaDtos.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/collections")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCmsCollectionController {

    private final AdminCmsCollectionService collectionService;

    public AdminCmsCollectionController(AdminCmsCollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @PostMapping
    public ResponseEntity<CollectionResponse> create(@Valid @RequestBody CollectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(collectionService.create(request));
    }

    @GetMapping
    public PageResponse<CollectionListItem> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return collectionService.list(query, status, country, page, size);
    }

    @GetMapping("/{collectionId}")
    public CollectionResponse get(@PathVariable Long collectionId) {
        return collectionService.get(collectionId);
    }

    @PatchMapping("/{collectionId}")
    public CollectionResponse update(
            @PathVariable Long collectionId,
            @Valid @RequestBody CollectionUpdateRequest request
    ) {
        return collectionService.update(collectionId, request);
    }

    @PostMapping("/{collectionId}/localizations")
    public CollectionResponse addLocalization(
            @PathVariable Long collectionId,
            @Valid @RequestBody CollectionLocalizationRequest request
    ) {
        return collectionService.addLocalization(collectionId, request);
    }

    @PutMapping("/{collectionId}/localizations/{locale}")
    public CollectionResponse updateLocalization(
            @PathVariable Long collectionId,
            @PathVariable String locale,
            @Valid @RequestBody CollectionLocalizationUpdateRequest request
    ) {
        return collectionService.updateLocalization(collectionId, locale, request);
    }

    @DeleteMapping("/{collectionId}/localizations/{locale}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLocalization(@PathVariable Long collectionId, @PathVariable String locale) {
        collectionService.deleteLocalization(collectionId, locale);
    }

    @PostMapping("/{collectionId}/items")
    public CollectionResponse addItems(
            @PathVariable Long collectionId,
            @Valid @RequestBody CollectionItemsRequest request
    ) {
        return collectionService.addItems(collectionId, request);
    }

    @DeleteMapping("/{collectionId}/items/{contentId}")
    public CollectionResponse removeItem(@PathVariable Long collectionId, @PathVariable Long contentId) {
        return collectionService.removeItem(collectionId, contentId);
    }

    @PutMapping("/{collectionId}/items/order")
    public CollectionResponse reorder(@PathVariable Long collectionId, @Valid @RequestBody CollectionItemsRequest request) {
        return collectionService.reorder(collectionId, request);
    }

    @GetMapping("/{collectionId}/availabilities")
    public List<CollectionAvailabilityResponse> listAvailabilities(@PathVariable Long collectionId) {
        return collectionService.listAvailabilities(collectionId);
    }

    @PutMapping("/{collectionId}/availabilities/{countryCode}")
    public CollectionResponse setAvailability(
            @PathVariable Long collectionId,
            @PathVariable String countryCode,
            @Valid @RequestBody CollectionAvailabilityRequest request
    ) {
        var normalized = new CollectionAvailabilityRequest(
                countryCode,
                request.availableFrom(),
                request.availableUntil(),
                request.status()
        );
        return collectionService.setAvailability(collectionId, normalized);
    }

    @DeleteMapping("/{collectionId}/availabilities/{countryCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAvailability(@PathVariable Long collectionId, @PathVariable String countryCode) {
        collectionService.deleteAvailability(collectionId, countryCode);
    }

    @PutMapping("/{collectionId}/availabilities")
    public CollectionResponse bulkSetAvailabilities(
            @PathVariable Long collectionId,
            @Valid @RequestBody CollectionAvailabilityBulkRequest request
    ) {
        return collectionService.bulkSetAvailabilities(collectionId, request);
    }

    @GetMapping("/{collectionId}/preview")
    public CollectionPreviewResponse preview(@PathVariable Long collectionId, @RequestParam String country) {
        return collectionService.preview(collectionId, country);
    }
}
