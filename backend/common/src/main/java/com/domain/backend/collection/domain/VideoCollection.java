package com.domain.backend.collection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "video_collections")
public class VideoCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String collectionKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String subtitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private VideoCollectionType collectionType;

    @Column(length = 200)
    private String referenceValue;

    @Column(nullable = false)
    private int itemLimit;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected VideoCollection() {
    }

    public VideoCollection(String collectionKey, String title, String subtitle, VideoCollectionType collectionType,
                           String referenceValue, int itemLimit, int displayOrder, boolean enabled) {
        this.collectionKey = collectionKey;
        this.title = title;
        this.subtitle = subtitle;
        this.collectionType = collectionType;
        this.referenceValue = referenceValue;
        this.itemLimit = itemLimit;
        this.displayOrder = displayOrder;
        this.enabled = enabled;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCollectionKey() { return collectionKey; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public VideoCollectionType getCollectionType() { return collectionType; }
    public String getReferenceValue() { return referenceValue; }
    public int getItemLimit() { return itemLimit; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String title, String subtitle, VideoCollectionType collectionType, String referenceValue,
                       int itemLimit, int displayOrder, boolean enabled) {
        this.title = title;
        this.subtitle = subtitle;
        this.collectionType = collectionType;
        this.referenceValue = referenceValue;
        this.itemLimit = itemLimit;
        this.displayOrder = displayOrder;
        this.enabled = enabled;
    }
}
