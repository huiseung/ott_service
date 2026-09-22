package com.domain.backend.content.application;

import com.domain.backend.content.application.AdminContentDtos.AvailabilityBulkRequest;
import com.domain.backend.content.application.AdminContentDtos.AvailabilityRequest;
import com.domain.backend.content.application.AdminContentDtos.AvailabilityResponse;
import com.domain.backend.content.application.AdminContentDtos.ContentGenreRequest;
import com.domain.backend.content.application.AdminContentDtos.ContentListItem;
import com.domain.backend.content.application.AdminContentDtos.ContentLocalizationRequest;
import com.domain.backend.content.application.AdminContentDtos.ContentLocalizationUpdateRequest;
import com.domain.backend.content.application.AdminContentDtos.ContentRequest;
import com.domain.backend.content.application.AdminContentDtos.ContentResponse;
import com.domain.backend.content.application.AdminContentDtos.LocalizationResponse;
import com.domain.backend.content.domain.Content;
import com.domain.backend.content.domain.ContentAvailability;
import com.domain.backend.content.domain.ContentImageType;
import com.domain.backend.content.domain.ContentStatus;
import com.domain.backend.content.domain.ContentType;
import com.domain.backend.content.domain.Genre;
import com.domain.backend.content.infrastructure.persistence.ContentAvailabilityRepository;
import com.domain.backend.content.infrastructure.persistence.ContentImageRepository;
import com.domain.backend.content.infrastructure.persistence.ContentLocalizationRepository;
import com.domain.backend.content.infrastructure.persistence.ContentRepository;
import com.domain.backend.content.infrastructure.persistence.GenreRepository;
import com.domain.backend.media.application.AdminMediaDtos.PageResponse;
import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
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
public class AdminContentService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ContentRepository contentRepository;
    private final ContentLocalizationRepository localizationRepository;
    private final ContentAvailabilityRepository availabilityRepository;
    private final ContentImageRepository contentImageRepository;
    private final GenreRepository genreRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectStorageClient storageClient;

    public AdminContentService(ContentRepository contentRepository,
                               ContentLocalizationRepository localizationRepository,
                               ContentAvailabilityRepository availabilityRepository,
                               ContentImageRepository contentImageRepository,
                               GenreRepository genreRepository,
                               JdbcTemplate jdbcTemplate,
                               ObjectStorageClient storageClient) {
        this.contentRepository = contentRepository;
        this.localizationRepository = localizationRepository;
        this.availabilityRepository = availabilityRepository;
        this.contentImageRepository = contentImageRepository;
        this.genreRepository = genreRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.storageClient = storageClient;
    }

    @Transactional
    public ContentResponse create(ContentRequest request) {
        Content content = contentRepository.save(new Content(
                request.type(),
                request.status(),
                normalizeCountry(request.originalCountry()),
                request.originalLanguage().trim(),
                request.releaseDate()
        ));
        return response(content);
    }

    @Transactional(readOnly = true)
    public ContentResponse get(Long contentId) {
        return response(requireContent(contentId));
    }

    @Transactional
    public ContentResponse update(Long contentId, ContentRequest request) {
        Content content = requireContent(contentId);
        content.update(
                request.type(),
                request.status(),
                normalizeCountry(request.originalCountry()),
                request.originalLanguage().trim(),
                request.releaseDate()
        );
        return response(content);
    }

    @Transactional
    public ContentResponse addLocalization(Long contentId, ContentLocalizationRequest request) {
        requireContent(contentId);
        String locale = normalizeLocale(request.locale());
        if (localizationRepository.existsByContentIdAndLocale(contentId, locale)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Content localization already exists");
        }
        localizationRepository.save(new com.domain.backend.content.domain.ContentLocalization(
                contentId,
                locale,
                request.title().trim(),
                trimToNull(request.shortDescription()),
                trimToNull(request.description())
        ));
        return get(contentId);
    }

    @Transactional
    public ContentResponse updateLocalization(Long contentId, String locale, ContentLocalizationUpdateRequest request) {
        requireContent(contentId);
        var localization = localizationRepository.findByContentIdAndLocale(contentId, normalizeLocale(locale))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content localization not found"));
        localization.update(
                request.title().trim(),
                trimToNull(request.shortDescription()),
                trimToNull(request.description())
        );
        return get(contentId);
    }

    @Transactional
    public void deleteLocalization(Long contentId, String locale) {
        requireContent(contentId);
        localizationRepository.deleteByContentIdAndLocale(contentId, normalizeLocale(locale));
    }

    @Transactional
    public ContentResponse replaceGenres(Long contentId, ContentGenreRequest request) {
        requireContent(contentId);
        Set<String> requestedCodes = request.genreCodes().stream()
                .map(this::normalizeGenreCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Genre> genres = genreRepository.findByCodeIn(requestedCodes);
        Set<String> foundCodes = genres.stream().map(Genre::getCode).collect(Collectors.toSet());
        List<String> missing = requestedCodes.stream().filter(code -> !foundCodes.contains(code)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Genre does not exist: " + missing.getFirst());
        }
        jdbcTemplate.update("delete from content_genres where content_id = ?", contentId);
        for (Genre genre : genres) {
            jdbcTemplate.update("insert into content_genres(content_id, genre_id) values (?, ?)", contentId, genre.getId());
        }
        return get(contentId);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listAvailabilities(Long contentId) {
        requireContent(contentId);
        return availabilityRepository.findByContentIdOrderByCountryCodeAsc(contentId).stream()
                .map(this::availabilityResponse)
                .toList();
    }

    @Transactional
    public ContentResponse setAvailability(Long contentId, AvailabilityRequest request) {
        replaceAvailability(contentId, request, true);
        return get(contentId);
    }

    @Transactional
    public ContentResponse bulkSetAvailabilities(Long contentId, AvailabilityBulkRequest request) {
        requireContent(contentId);
        Set<String> seen = new HashSet<>();
        for (AvailabilityRequest availability : request.availabilities()) {
            String countryCode = normalizeCountry(availability.countryCode());
            if (!seen.add(countryCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate availability country: " + countryCode);
            }
            validateWindow(availability.availableFrom(), availability.availableUntil());
        }
        for (AvailabilityRequest availability : request.availabilities()) {
            replaceAvailability(contentId, availability, false);
        }
        return get(contentId);
    }

    @Transactional(readOnly = true)
    public PageResponse<ContentListItem> list(String query, ContentType type, String genre, String country,
                                              ContentStatus status, Integer page, Integer size) {
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        List<Object> params = new ArrayList<>();
        String where = whereClause(query, type, genre, country, status, params);
        Long total = jdbcTemplate.queryForObject("select count(*) from contents c" + where, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(safeSize);
        pageParams.add((long) safePage * safeSize);
        List<ContentListItem> items = jdbcTemplate.query("""
                        select c.id, c.type, c.status, c.original_country, c.original_language,
                               c.release_date, c.created_at, c.updated_at
                        from contents c
                        %s
                        order by c.created_at desc, c.id desc
                        limit ? offset ?
                        """.formatted(where),
                (rs, rowNum) -> new ContentListItem(
                        rs.getLong("id"),
                        ContentType.valueOf(rs.getString("type")),
                        ContentStatus.valueOf(rs.getString("status")),
                        rs.getString("original_country"),
                        rs.getString("original_language"),
                        rs.getDate("release_date") == null ? null : rs.getDate("release_date").toLocalDate(),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(),
                        null
                ),
                pageParams.toArray());
        Map<Long, String> landscapeUrls = landscapeUrls(items.stream().map(ContentListItem::id).toList());
        items = items.stream()
                .map(item -> new ContentListItem(
                        item.id(),
                        item.type(),
                        item.status(),
                        item.originalCountry(),
                        item.originalLanguage(),
                        item.releaseDate(),
                        item.createdAt(),
                        item.updatedAt(),
                        landscapeUrls.get(item.id())
                ))
                .toList();
        long totalElements = total == null ? 0 : total;
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        return new PageResponse<>(items, safePage, safeSize, totalElements, totalPages, safePage == 0, safePage + 1 >= totalPages);
    }

    private String whereClause(String query, ContentType type, String genre, String country, ContentStatus status,
                               List<Object> params) {
        List<String> clauses = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            String trimmed = query.trim();
            if (trimmed.matches("\\d+")) {
                clauses.add("c.id = ?");
                params.add(Long.valueOf(trimmed));
            } else {
                clauses.add("""
                        exists (
                            select 1 from content_localizations cl
                            where cl.content_id = c.id and cl.title like ?
                        )
                        """);
                params.add("%" + trimmed + "%");
            }
        }
        if (type != null) {
            clauses.add("c.type = ?");
            params.add(type.name());
        }
        if (status != null) {
            clauses.add("c.status = ?");
            params.add(status.name());
        }
        if (genre != null && !genre.isBlank()) {
            clauses.add("""
                    exists (
                        select 1 from content_genres cg
                        join genres g on g.id = cg.genre_id
                        where cg.content_id = c.id and g.code = ?
                    )
                    """);
            params.add(normalizeGenreCode(genre));
        }
        if (country != null && !country.isBlank()) {
            clauses.add("""
                    exists (
                        select 1 from content_availabilities ca
                        where ca.content_id = c.id and ca.country_code = ?
                    )
                    """);
            params.add(normalizeCountry(country));
        }
        return clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
    }

    private void replaceAvailability(Long contentId, AvailabilityRequest request, boolean requireContent) {
        if (requireContent) {
            requireContent(contentId);
        }
        String countryCode = normalizeCountry(request.countryCode());
        validateWindow(request.availableFrom(), request.availableUntil());
        var availability = availabilityRepository.findByContentIdAndCountryCode(contentId, countryCode)
                .orElse(null);
        if (availability == null) {
            availabilityRepository.save(new ContentAvailability(
                    contentId,
                    countryCode,
                    request.availableFrom(),
                    request.availableUntil(),
                    request.status()
            ));
        } else {
            availability.update(request.availableFrom(), request.availableUntil(), request.status());
        }
    }

    private Content requireContent(Long contentId) {
        return contentRepository.findById(contentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found"));
    }

    private ContentResponse response(Content content) {
        List<LocalizationResponse> localizations = localizationRepository.findByContentIdOrderByLocaleAsc(content.getId()).stream()
                .map(localization -> new LocalizationResponse(
                        localization.getId(),
                        localization.getLocale(),
                        localization.getTitle(),
                        localization.getShortDescription(),
                        localization.getDescription(),
                        localization.getCreatedAt(),
                        localization.getUpdatedAt()
                ))
                .toList();
        List<String> genreCodes = jdbcTemplate.query("""
                        select g.code
                        from content_genres cg
                        join genres g on g.id = cg.genre_id
                        where cg.content_id = ?
                        order by g.sort_order asc, g.code asc
                        """,
                (rs, rowNum) -> rs.getString("code"),
                content.getId());
        List<AvailabilityResponse> availabilities = availabilityRepository.findByContentIdOrderByCountryCodeAsc(content.getId()).stream()
                .map(this::availabilityResponse)
                .toList();
        return new ContentResponse(
                content.getId(),
                content.getType(),
                content.getStatus(),
                content.getOriginalCountry(),
                content.getOriginalLanguage(),
                content.getReleaseDate(),
                content.getCreatedAt(),
                content.getUpdatedAt(),
                localizations,
                genreCodes,
                availabilities
        );
    }

    private AvailabilityResponse availabilityResponse(ContentAvailability availability) {
        return new AvailabilityResponse(
                availability.getId(),
                availability.getCountryCode(),
                availability.getAvailableFrom(),
                availability.getAvailableUntil(),
                availability.getStatus()
        );
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

    private String normalizeGenreCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Genre code is required");
        }
        return normalized;
    }

    private void validateWindow(Instant availableFrom, Instant availableUntil) {
        if (availableUntil != null && availableUntil.isBefore(availableFrom)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "availableUntil must be greater than or equal to availableFrom");
        }
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
}
