package com.domain.backend.collection.domain;

import com.domain.backend.content.domain.ContentAvailabilityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "collection_availabilities")
public class CmsCollectionAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long collectionId;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false)
    private Instant availableFrom;

    private Instant availableUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ContentAvailabilityStatus status;

    protected CmsCollectionAvailability() {
    }

    public CmsCollectionAvailability(Long collectionId, String countryCode, Instant availableFrom,
                                     Instant availableUntil, ContentAvailabilityStatus status) {
        this.collectionId = collectionId;
        this.countryCode = countryCode;
        this.availableFrom = availableFrom;
        this.availableUntil = availableUntil;
        this.status = status;
    }

    public Long getId() { return id; }
    public Long getCollectionId() { return collectionId; }
    public String getCountryCode() { return countryCode; }
    public Instant getAvailableFrom() { return availableFrom; }
    public Instant getAvailableUntil() { return availableUntil; }
    public ContentAvailabilityStatus getStatus() { return status; }

    public void update(Instant availableFrom, Instant availableUntil, ContentAvailabilityStatus status) {
        this.availableFrom = availableFrom;
        this.availableUntil = availableUntil;
        this.status = status;
    }
}
