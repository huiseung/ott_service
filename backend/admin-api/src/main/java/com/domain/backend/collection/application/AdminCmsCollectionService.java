package com.domain.backend.collection.application;

import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionAvailabilityBulkRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionAvailabilityRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionAvailabilityResponse;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionItemResponse;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionItemsRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionListItem;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionLocalizationRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionLocalizationResponse;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionLocalizationUpdateRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionPreviewItem;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionPreviewResponse;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionResponse;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.CollectionUpdateRequest;
import com.domain.backend.collection.application.AdminCmsCollectionDtos.PreviewReason;
import com.domain.backend.collection.domain.CmsCollection;
import com.domain.backend.collection.domain.CmsCollectionAvailability;
import com.domain.backend.collection.domain.CmsCollectionItem;
import com.domain.backend.collection.domain.CmsCollectionLocalization;
import com.domain.backend.collection.infrastructure.persistence.CmsCollectionAvailabilityRepository;
import com.domain.backend.collection.infrastructure.persistence.CmsCollectionItemRepository;
import com.domain.backend.collection.infrastructure.persistence.CmsCollectionLocalizationRepository;
import com.domain.backend.collection.infrastructure.persistence.CmsCollectionRepository;
import com.domain.backend.content.domain.ContentAvailabilityStatus;
import com.domain.backend.content.domain.ContentImageType;
import com.domain.backend.content.domain.ContentStatus;
import com.domain.backend.content.domain.ContentType;
import com.domain.backend.content.infrastructure.persistence.ContentImageRepository;
import com.domain.backend.content.infrastructure.persistence.ContentRepository;
import com.domain.backend.media.application.AdminMediaDtos.PageResponse;
import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminCmsCollectionService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int REORDER_TEMP_OFFSET = 1_000_000;

    private final CmsCollectionRepository collectionRepository;
    private final CmsCollectionLocalizationRepository localizationRepository;
    private final CmsCollectionItemRepository itemRepository;
    private final CmsCollectionAvailabilityRepository availabilityRepository;
    private final ContentRepository contentRepository;
    private final ContentImageRepository contentImageRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final ObjectStorageClient storageClient;

    public AdminCmsCollectionService(CmsCollectionRepository collectionRepository,
                                     CmsCollectionLocalizationRepository localizationRepository,
                                     CmsCollectionItemRepository itemRepository,
                                     CmsCollectionAvailabilityRepository availabilityRepository,
                                     ContentRepository contentRepository,
                                     ContentImageRepository contentImageRepository,
                                     JdbcTemplate jdbcTemplate,
                                     Clock clock,
                                     ObjectStorageClient storageClient) {
        this.collectionRepository = collectionRepository;
        this.localizationRepository = localizationRepository;
        this.itemRepository = itemRepository;
        this.availabilityRepository = availabilityRepository;
        this.contentRepository = contentRepository;
        this.contentImageRepository = contentImageRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.storageClient = storageClient;
    }

    @Transactional
    public CollectionResponse create(CollectionRequest request) {
        CmsCollection collection = collectionRepository.save(new CmsCollection(request.status(), request.minVisibleItems()));
        validateLocalizations(request.localizations());
        if (request.localizations() != null) {
            for (CollectionLocalizationRequest localization : request.localizations()) {
                localizationRepository.save(new CmsCollectionLocalization(
                        collection.getId(),
                        normalizeLocale(localization.locale()),
                        localization.title().trim(),
                        trimToNull(localization.description())
                ));
            }
        }
        return response(collection);
    }

    @Transactional(readOnly = true)
    public PageResponse<CollectionListItem> list(String query, ContentStatus status, String country, Integer page, Integer size) {
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        List<Object> params = new ArrayList<>();
        String where = collectionWhereClause(query, status, country, params);
        Long total = jdbcTemplate.queryForObject("select count(*) from collections c" + where, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(safeSize);
        pageParams.add((long) safePage * safeSize);
        List<CollectionListItem> items = jdbcTemplate.query("""
                        select c.id, c.status, c.min_visible_items, c.version, c.created_at, c.updated_at
                        from collections c
                        %s
                        order by c.created_at desc, c.id desc
                        limit ? offset ?
                        """.formatted(where),
                (rs, rowNum) -> new CollectionListItem(
                        rs.getLong("id"),
                        ContentStatus.valueOf(rs.getString("status")),
                        rs.getInt("min_visible_items"),
                        rs.getLong("version"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                pageParams.toArray());
        long totalElements = total == null ? 0 : total;
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        return new PageResponse<>(items, safePage, safeSize, totalElements, totalPages, safePage == 0, safePage + 1 >= totalPages);
    }

    @Transactional(readOnly = true)
    public CollectionResponse get(Long collectionId) {
        return response(requireCollection(collectionId));
    }

    @Transactional
    public CollectionResponse update(Long collectionId, CollectionUpdateRequest request) {
        CmsCollection collection = requireCollection(collectionId);
        requireMutable(collection);
        collection.update(request.status(), request.minVisibleItems());
        return response(collection);
    }

    @Transactional
    public CollectionResponse addLocalization(Long collectionId, CollectionLocalizationRequest request) {
        CmsCollection collection = requireMutableCollection(collectionId);
        String locale = normalizeLocale(request.locale());
        if (localizationRepository.existsByCollectionIdAndLocale(collectionId, locale)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Collection localization already exists");
        }
        localizationRepository.save(new CmsCollectionLocalization(
                collectionId,
                locale,
                request.title().trim(),
                trimToNull(request.description())
        ));
        collection.touch();
        return response(collection);
    }

    @Transactional
    public CollectionResponse updateLocalization(Long collectionId, String locale, CollectionLocalizationUpdateRequest request) {
        CmsCollection collection = requireMutableCollection(collectionId);
        CmsCollectionLocalization localization = localizationRepository.findByCollectionIdAndLocale(collectionId, normalizeLocale(locale))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection localization not found"));
        localization.update(request.title().trim(), trimToNull(request.description()));
        collection.touch();
        return response(collection);
    }

    @Transactional
    public void deleteLocalization(Long collectionId, String locale) {
        CmsCollection collection = requireMutableCollection(collectionId);
        localizationRepository.deleteByCollectionIdAndLocale(collectionId, normalizeLocale(locale));
        collection.touch();
    }

    @Transactional
    public CollectionResponse addItems(Long collectionId, CollectionItemsRequest request) {
        CmsCollection collection = requireMutableCollection(collectionId);
        Set<Long> requested = distinctContentIds(request.contentIds());
        validateContentsExist(requested);
        List<CmsCollectionItem> existing = itemRepository.findByCollectionIdAndContentIdIn(collectionId, requested);
        if (!existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Content already exists in collection: " + existing.getFirst().getContentId());
        }
        int nextOrder = (int) itemRepository.countByCollectionId(collectionId) + 1;
        for (Long contentId : request.contentIds()) {
            itemRepository.save(new CmsCollectionItem(collectionId, contentId, nextOrder++));
        }
        collection.touch();
        return response(collection);
    }

    @Transactional
    public CollectionResponse removeItem(Long collectionId, Long contentId) {
        CmsCollection collection = requireMutableCollection(collectionId);
        itemRepository.findByCollectionIdAndContentId(collectionId, contentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection item not found"));
        jdbcTemplate.update("delete from collection_items where collection_id = ? and content_id = ?", collectionId, contentId);
        normalizeItemOrder(collectionId);
        collection.touch();
        return response(collection);
    }

    @Transactional
    public CollectionResponse reorder(Long collectionId, CollectionItemsRequest request) {
        CmsCollection collection = requireMutableCollection(collectionId);
        List<CmsCollectionItem> current = itemRepository.findByCollectionIdOrderByDisplayOrderAsc(collectionId);
        Set<Long> requested = distinctContentIds(request.contentIds());
        Set<Long> currentContentIds = current.stream().map(CmsCollectionItem::getContentId).collect(Collectors.toSet());
        if (!requested.equals(currentContentIds) || request.contentIds().size() != current.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reorder request must contain every current collection content exactly once");
        }
        jdbcTemplate.update("update collection_items set display_order = display_order + ? where collection_id = ?",
                REORDER_TEMP_OFFSET,
                collectionId);
        int order = 1;
        for (Long contentId : request.contentIds()) {
            jdbcTemplate.update("update collection_items set display_order = ? where collection_id = ? and content_id = ?",
                    order++,
                    collectionId,
                    contentId);
        }
        collection.touch();
        return response(collection);
    }

    private void normalizeItemOrder(Long collectionId) {
        jdbcTemplate.update("update collection_items set display_order = display_order + ? where collection_id = ?",
                REORDER_TEMP_OFFSET,
                collectionId);
        List<Long> orderedIds = jdbcTemplate.query("""
                        select id
                        from collection_items
                        where collection_id = ?
                        order by display_order asc, id asc
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                collectionId);
        int order = 1;
        for (Long itemId : orderedIds) {
            jdbcTemplate.update("update collection_items set display_order = ? where id = ?", order++, itemId);
        }
    }

    @Transactional(readOnly = true)
    public List<CollectionAvailabilityResponse> listAvailabilities(Long collectionId) {
        requireCollection(collectionId);
        return availabilityRepository.findByCollectionIdOrderByCountryCodeAsc(collectionId).stream()
                .map(this::availabilityResponse)
                .toList();
    }

    @Transactional
    public CollectionResponse setAvailability(Long collectionId, CollectionAvailabilityRequest request) {
        CmsCollection collection = requireMutableCollection(collectionId);
        replaceAvailability(collectionId, request);
        collection.touch();
        return response(collection);
    }

    @Transactional
    public void deleteAvailability(Long collectionId, String countryCode) {
        CmsCollection collection = requireMutableCollection(collectionId);
        availabilityRepository.deleteByCollectionIdAndCountryCode(collectionId, normalizeCountry(countryCode));
        collection.touch();
    }

    @Transactional
    public CollectionResponse bulkSetAvailabilities(Long collectionId, CollectionAvailabilityBulkRequest request) {
        CmsCollection collection = requireMutableCollection(collectionId);
        Set<String> seen = new HashSet<>();
        for (CollectionAvailabilityRequest availability : request.availabilities()) {
            String countryCode = normalizeCountry(availability.countryCode());
            if (!seen.add(countryCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate availability country: " + countryCode);
            }
            validateWindow(availability.availableFrom(), availability.availableUntil());
        }
        for (CollectionAvailabilityRequest availability : request.availabilities()) {
            replaceAvailability(collectionId, availability);
        }
        collection.touch();
        return response(collection);
    }

    @Transactional(readOnly = true)
    public CollectionPreviewResponse preview(Long collectionId, String country) {
        CmsCollection collection = requireCollection(collectionId);
        String countryCode = normalizeCountry(country);
        Instant now = Instant.now(clock);
        CmsCollectionAvailability collectionAvailability = availabilityRepository
                .findByCollectionIdAndCountryCode(collectionId, countryCode)
                .orElse(null);
        PreviewReason collectionReason = collectionReason(collection, collectionAvailability, now);
        boolean collectionVisible = collectionReason == null;
        List<PreviewRow> rows = previewRows(collectionId, countryCode);
        List<CollectionPreviewItem> items = rows.stream()
                .map(row -> {
                    PreviewReason reason = contentReason(row, now);
                    return new CollectionPreviewItem(row.contentId(), row.displayOrder(), reason == null, reason);
                })
                .toList();
        int visibleItems = (int) items.stream().filter(CollectionPreviewItem::visible).count();
        boolean displayable = collectionVisible && visibleItems >= collection.getMinVisibleItems();
        return new CollectionPreviewResponse(
                collectionId,
                countryCode,
                collectionVisible,
                collectionReason,
                items.size(),
                visibleItems,
                collection.getMinVisibleItems(),
                displayable,
                items
        );
    }

    private String collectionWhereClause(String query, ContentStatus status, String country, List<Object> params) {
        List<String> clauses = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            clauses.add("""
                    exists (
                        select 1 from collection_localizations cl
                        where cl.collection_id = c.id and cl.title like ?
                    )
                    """);
            params.add("%" + query.trim() + "%");
        }
        if (status != null) {
            clauses.add("c.status = ?");
            params.add(status.name());
        }
        if (country != null && !country.isBlank()) {
            clauses.add("""
                    exists (
                        select 1 from collection_availabilities ca
                        where ca.collection_id = c.id and ca.country_code = ?
                    )
                    """);
            params.add(normalizeCountry(country));
        }
        return clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
    }

    private void replaceAvailability(Long collectionId, CollectionAvailabilityRequest request) {
        String countryCode = normalizeCountry(request.countryCode());
        validateWindow(request.availableFrom(), request.availableUntil());
        CmsCollectionAvailability availability = availabilityRepository.findByCollectionIdAndCountryCode(collectionId, countryCode)
                .orElse(null);
        if (availability == null) {
            availabilityRepository.save(new CmsCollectionAvailability(
                    collectionId,
                    countryCode,
                    request.availableFrom(),
                    request.availableUntil(),
                    request.status()
            ));
        } else {
            availability.update(request.availableFrom(), request.availableUntil(), request.status());
        }
    }

    private CollectionResponse response(CmsCollection collection) {
        return new CollectionResponse(
                collection.getId(),
                collection.getStatus(),
                collection.getMinVisibleItems(),
                collection.getVersion(),
                collection.getCreatedAt(),
                collection.getUpdatedAt(),
                localizationRepository.findByCollectionIdOrderByLocaleAsc(collection.getId()).stream()
                        .map(this::localizationResponse)
                        .toList(),
                availabilityRepository.findByCollectionIdOrderByCountryCodeAsc(collection.getId()).stream()
                        .map(this::availabilityResponse)
                        .toList(),
                itemResponses(collection.getId())
        );
    }

    private List<CollectionItemResponse> itemResponses(Long collectionId) {
        List<CollectionItemResponse> items = jdbcTemplate.query("""
                        select ci.id, ci.content_id, c.type, c.status, ci.display_order, ci.created_at,
                               (
                                   select cl.title
                                   from content_localizations cl
                                   where cl.content_id = ci.content_id
                                   order by cl.locale asc
                                   limit 1
                               ) as title
                        from collection_items ci
                        join contents c on c.id = ci.content_id
                        where ci.collection_id = ?
                        order by ci.display_order asc, ci.id asc
                        """,
                (rs, rowNum) -> new CollectionItemResponse(
                        rs.getLong("id"),
                        rs.getLong("content_id"),
                        ContentType.valueOf(rs.getString("type")),
                        ContentStatus.valueOf(rs.getString("status")),
                        rs.getInt("display_order"),
                        rs.getString("title"),
                        null,
                        rs.getTimestamp("created_at").toInstant()
                ),
                collectionId);
        Map<Long, String> landscapeUrls = landscapeUrls(items.stream().map(CollectionItemResponse::contentId).toList());
        return items.stream()
                .map(item -> new CollectionItemResponse(
                        item.id(),
                        item.contentId(),
                        item.type(),
                        item.status(),
                        item.displayOrder(),
                        item.title(),
                        landscapeUrls.get(item.contentId()),
                        item.createdAt()
                ))
                .toList();
    }

    private CollectionLocalizationResponse localizationResponse(CmsCollectionLocalization localization) {
        return new CollectionLocalizationResponse(
                localization.getId(),
                localization.getLocale(),
                localization.getTitle(),
                localization.getDescription(),
                localization.getCreatedAt(),
                localization.getUpdatedAt()
        );
    }

    private CollectionAvailabilityResponse availabilityResponse(CmsCollectionAvailability availability) {
        return new CollectionAvailabilityResponse(
                availability.getId(),
                availability.getCountryCode(),
                availability.getAvailableFrom(),
                availability.getAvailableUntil(),
                availability.getStatus()
        );
    }

    private List<PreviewRow> previewRows(Long collectionId, String countryCode) {
        return jdbcTemplate.query("""
                        select ci.content_id, ci.display_order, c.status as content_status,
                               ca.status as availability_status,
                               ca.available_from,
                               ca.available_until
                        from collection_items ci
                        join contents c on c.id = ci.content_id
                        left join content_availabilities ca
                          on ca.content_id = ci.content_id and ca.country_code = ?
                        where ci.collection_id = ?
                        order by ci.display_order asc, ci.id asc
                        """,
                (rs, rowNum) -> new PreviewRow(
                        rs.getLong("content_id"),
                        rs.getInt("display_order"),
                        ContentStatus.valueOf(rs.getString("content_status")),
                        rs.getString("availability_status") == null ? null : ContentAvailabilityStatus.valueOf(rs.getString("availability_status")),
                        rs.getTimestamp("available_from") == null ? null : rs.getTimestamp("available_from").toInstant(),
                        rs.getTimestamp("available_until") == null ? null : rs.getTimestamp("available_until").toInstant()
                ),
                countryCode,
                collectionId);
    }

    private PreviewReason collectionReason(CmsCollection collection, CmsCollectionAvailability availability, Instant now) {
        if (collection.getStatus() != ContentStatus.PUBLISHED) {
            return PreviewReason.COLLECTION_NOT_PUBLISHED;
        }
        if (availability == null) {
            return PreviewReason.COLLECTION_TERRITORY_NOT_CONFIGURED;
        }
        if (availability.getStatus() == ContentAvailabilityStatus.DISABLED) {
            return PreviewReason.COLLECTION_TERRITORY_DISABLED;
        }
        if (availability.getAvailableFrom().isAfter(now)) {
            return PreviewReason.COLLECTION_NOT_YET_AVAILABLE;
        }
        if (availability.getAvailableUntil() != null && !now.isBefore(availability.getAvailableUntil())) {
            return PreviewReason.COLLECTION_AVAILABILITY_EXPIRED;
        }
        return null;
    }

    private PreviewReason contentReason(PreviewRow row, Instant now) {
        if (row.contentStatus() != ContentStatus.PUBLISHED) {
            return PreviewReason.CONTENT_NOT_PUBLISHED;
        }
        if (row.availabilityStatus() == null) {
            return PreviewReason.CONTENT_TERRITORY_NOT_CONFIGURED;
        }
        if (row.availabilityStatus() == ContentAvailabilityStatus.DISABLED) {
            return PreviewReason.CONTENT_TERRITORY_DISABLED;
        }
        if (row.availableFrom().isAfter(now)) {
            return PreviewReason.CONTENT_NOT_YET_AVAILABLE;
        }
        if (row.availableUntil() != null && !now.isBefore(row.availableUntil())) {
            return PreviewReason.CONTENT_AVAILABILITY_EXPIRED;
        }
        return null;
    }

    private CmsCollection requireCollection(Long collectionId) {
        return collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection not found"));
    }

    private CmsCollection requireMutableCollection(Long collectionId) {
        CmsCollection collection = requireCollection(collectionId);
        requireMutable(collection);
        return collection;
    }

    private void requireMutable(CmsCollection collection) {
        if (collection.getStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived collection cannot be modified");
        }
    }

    private Set<Long> distinctContentIds(List<Long> contentIds) {
        Set<Long> distinct = new LinkedHashSet<>(contentIds);
        if (distinct.size() != contentIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate content id is not allowed");
        }
        return distinct;
    }

    private void validateContentsExist(Set<Long> contentIds) {
        Set<Long> existing = contentRepository.findAllById(contentIds).stream()
                .map(com.domain.backend.content.domain.Content::getId)
                .collect(Collectors.toSet());
        List<Long> missing = contentIds.stream().filter(id -> !existing.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content does not exist: " + missing.getFirst());
        }
    }

    private void validateLocalizations(List<CollectionLocalizationRequest> localizations) {
        if (localizations == null || localizations.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (CollectionLocalizationRequest localization : localizations) {
            String locale = normalizeLocale(localization.locale());
            if (!seen.add(locale)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate localization locale: " + locale);
            }
        }
    }

    private void validateWindow(Instant availableFrom, Instant availableUntil) {
        if (availableUntil != null && availableUntil.isBefore(availableFrom)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "availableUntil must be greater than or equal to availableFrom");
        }
    }

    private String normalizeCountry(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid country code");
        }
        return normalized;
    }

    private String normalizeLocale(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Locale is required");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Map<Long, String> landscapeUrls(List<Long> contentIds) {
        if (contentIds.isEmpty()) {
            return Map.of();
        }
        return contentImageRepository.findByContentIdInAndImageType(contentIds, ContentImageType.LANDSCAPE).stream()
                .collect(Collectors.toMap(
                        image -> image.getContentId(),
                        image -> storageClient.presignGetObject(image.getObjectKey()).toString()
                ));
    }

    private record PreviewRow(
            Long contentId,
            int displayOrder,
            ContentStatus contentStatus,
            ContentAvailabilityStatus availabilityStatus,
            Instant availableFrom,
            Instant availableUntil
    ) {
    }
}
