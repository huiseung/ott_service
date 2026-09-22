package com.domain.backend.content.application;

import com.domain.backend.content.domain.ContentAvailabilityStatus;
import com.domain.backend.content.domain.ContentStatus;
import com.domain.backend.content.domain.ContentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class AdminContentDtos {

    private AdminContentDtos() {
    }

    public record ContentRequest(
            @NotNull ContentType type,
            @NotNull ContentStatus status,
            @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String originalCountry,
            @NotBlank @Size(max = 20) String originalLanguage,
            LocalDate releaseDate
    ) {
    }

    public record ContentLocalizationRequest(
            @NotBlank @Size(max = 20) String locale,
            @NotBlank @Size(max = 300) String title,
            @Size(max = 500) String shortDescription,
            @Size(max = 4000) String description
    ) {
    }

    public record ContentLocalizationUpdateRequest(
            @NotBlank @Size(max = 300) String title,
            @Size(max = 500) String shortDescription,
            @Size(max = 4000) String description
    ) {
    }

    public record ContentGenreRequest(@NotNull Set<@NotBlank @Size(max = 50) String> genreCodes) {
    }

    public record AvailabilityRequest(
            @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
            @NotNull Instant availableFrom,
            Instant availableUntil,
            @NotNull ContentAvailabilityStatus status
    ) {
    }

    public record AvailabilityBulkRequest(@NotEmpty @Valid List<AvailabilityRequest> availabilities) {
    }

    public record ContentResponse(
            Long id,
            ContentType type,
            ContentStatus status,
            String originalCountry,
            String originalLanguage,
            LocalDate releaseDate,
            Instant createdAt,
            Instant updatedAt,
            List<LocalizationResponse> localizations,
            List<String> genreCodes,
            List<AvailabilityResponse> availabilities
    ) {
    }

    public record ContentListItem(
            Long id,
            ContentType type,
            ContentStatus status,
            String originalCountry,
            String originalLanguage,
            LocalDate releaseDate,
            Instant createdAt,
            Instant updatedAt,
            String landscapeImageUrl
    ) {
    }

    public record LocalizationResponse(
            Long id,
            String locale,
            String title,
            String shortDescription,
            String description,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record AvailabilityResponse(
            Long id,
            String countryCode,
            Instant availableFrom,
            Instant availableUntil,
            ContentAvailabilityStatus status
    ) {
    }
}
