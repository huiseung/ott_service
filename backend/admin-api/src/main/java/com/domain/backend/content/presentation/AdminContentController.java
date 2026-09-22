package com.domain.backend.content.presentation;

import com.domain.backend.content.application.AdminContentDtos.AvailabilityBulkRequest;
import com.domain.backend.content.application.AdminContentDtos.AvailabilityRequest;
import com.domain.backend.content.application.AdminContentDtos.AvailabilityResponse;
import com.domain.backend.content.application.AdminContentDtos.ContentGenreRequest;
import com.domain.backend.content.application.AdminContentDtos.ContentListItem;
import com.domain.backend.content.application.AdminContentDtos.ContentLocalizationRequest;
import com.domain.backend.content.application.AdminContentDtos.ContentLocalizationUpdateRequest;
import com.domain.backend.content.application.AdminContentDtos.ContentRequest;
import com.domain.backend.content.application.AdminContentDtos.ContentResponse;
import com.domain.backend.content.application.AdminContentService;
import com.domain.backend.content.domain.ContentStatus;
import com.domain.backend.content.domain.ContentType;
import com.domain.backend.media.application.AdminMediaDtos.PageResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/contents")
@PreAuthorize("hasRole('ADMIN')")
public class AdminContentController {

    private final AdminContentService contentService;

    public AdminContentController(AdminContentService contentService) {
        this.contentService = contentService;
    }

    @PostMapping
    public ResponseEntity<ContentResponse> create(@Valid @RequestBody ContentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contentService.create(request));
    }

    @GetMapping
    public PageResponse<ContentListItem> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) ContentType type,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return contentService.list(query, type, genre, country, status, page, size);
    }

    @GetMapping("/{contentId}")
    public ContentResponse get(@PathVariable Long contentId) {
        return contentService.get(contentId);
    }

    @PutMapping("/{contentId}")
    public ContentResponse update(@PathVariable Long contentId, @Valid @RequestBody ContentRequest request) {
        return contentService.update(contentId, request);
    }

    @PostMapping("/{contentId}/localizations")
    public ContentResponse addLocalization(
            @PathVariable Long contentId,
            @Valid @RequestBody ContentLocalizationRequest request
    ) {
        return contentService.addLocalization(contentId, request);
    }

    @PutMapping("/{contentId}/localizations/{locale}")
    public ContentResponse updateLocalization(
            @PathVariable Long contentId,
            @PathVariable String locale,
            @Valid @RequestBody ContentLocalizationUpdateRequest request
    ) {
        return contentService.updateLocalization(contentId, locale, request);
    }

    @DeleteMapping("/{contentId}/localizations/{locale}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLocalization(@PathVariable Long contentId, @PathVariable String locale) {
        contentService.deleteLocalization(contentId, locale);
    }

    @PutMapping("/{contentId}/genres")
    public ContentResponse replaceGenres(@PathVariable Long contentId, @Valid @RequestBody ContentGenreRequest request) {
        return contentService.replaceGenres(contentId, request);
    }

    @GetMapping("/{contentId}/availabilities")
    public List<AvailabilityResponse> listAvailabilities(@PathVariable Long contentId) {
        return contentService.listAvailabilities(contentId);
    }

    @PutMapping("/{contentId}/availabilities/{countryCode}")
    public ContentResponse setAvailability(
            @PathVariable Long contentId,
            @PathVariable String countryCode,
            @Valid @RequestBody AvailabilityRequest request
    ) {
        AvailabilityRequest normalized = new AvailabilityRequest(
                countryCode,
                request.availableFrom(),
                request.availableUntil(),
                request.status()
        );
        return contentService.setAvailability(contentId, normalized);
    }

    @PutMapping("/{contentId}/availabilities")
    public ContentResponse bulkSetAvailabilities(
            @PathVariable Long contentId,
            @Valid @RequestBody AvailabilityBulkRequest request
    ) {
        return contentService.bulkSetAvailabilities(contentId, request);
    }
}
