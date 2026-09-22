package com.domain.backend.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "content_localizations")
public class ContentLocalization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long contentId;

    @Column(nullable = false, length = 20)
    private String locale;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 500)
    private String shortDescription;

    @Column(length = 4000)
    private String description;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ContentLocalization() {
    }

    public ContentLocalization(Long contentId, String locale, String title, String shortDescription, String description) {
        this.contentId = contentId;
        this.locale = locale;
        this.title = title;
        this.shortDescription = shortDescription;
        this.description = description;
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
    public Long getContentId() { return contentId; }
    public String getLocale() { return locale; }
    public String getTitle() { return title; }
    public String getShortDescription() { return shortDescription; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String title, String shortDescription, String description) {
        this.title = title;
        this.shortDescription = shortDescription;
        this.description = description;
    }
}
