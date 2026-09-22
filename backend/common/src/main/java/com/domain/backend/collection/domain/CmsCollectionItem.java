package com.domain.backend.collection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "collection_items")
public class CmsCollectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long collectionId;

    @Column(nullable = false)
    private Long contentId;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private Instant createdAt;

    protected CmsCollectionItem() {
    }

    public CmsCollectionItem(Long collectionId, Long contentId, int displayOrder) {
        this.collectionId = collectionId;
        this.contentId = contentId;
        this.displayOrder = displayOrder;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getCollectionId() { return collectionId; }
    public Long getContentId() { return contentId; }
    public int getDisplayOrder() { return displayOrder; }
    public Instant getCreatedAt() { return createdAt; }

    public void updateDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
