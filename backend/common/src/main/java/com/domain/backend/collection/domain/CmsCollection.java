package com.domain.backend.collection.domain;

import com.domain.backend.content.domain.ContentStatus;
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
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "collections")
public class CmsCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ContentStatus status;

    @Column(nullable = false)
    private int minVisibleItems;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected CmsCollection() {
    }

    public CmsCollection(ContentStatus status, int minVisibleItems) {
        this.status = status;
        this.minVisibleItems = minVisibleItems;
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
    public ContentStatus getStatus() { return status; }
    public int getMinVisibleItems() { return minVisibleItems; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(ContentStatus status, int minVisibleItems) {
        this.status = status;
        this.minVisibleItems = minVisibleItems;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
