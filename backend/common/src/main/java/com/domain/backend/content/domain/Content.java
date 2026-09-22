package com.domain.backend.content.domain;

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
import java.time.LocalDate;

@Entity
@Table(name = "contents")
public class Content {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ContentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ContentStatus status;

    @Column(nullable = false, length = 2)
    private String originalCountry;

    @Column(nullable = false, length = 20)
    private String originalLanguage;

    private LocalDate releaseDate;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Content() {
    }

    public Content(ContentType type, ContentStatus status, String originalCountry, String originalLanguage,
                   LocalDate releaseDate) {
        this.type = type;
        this.status = status;
        this.originalCountry = originalCountry;
        this.originalLanguage = originalLanguage;
        this.releaseDate = releaseDate;
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
    public ContentType getType() { return type; }
    public ContentStatus getStatus() { return status; }
    public String getOriginalCountry() { return originalCountry; }
    public String getOriginalLanguage() { return originalLanguage; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(ContentType type, ContentStatus status, String originalCountry, String originalLanguage,
                       LocalDate releaseDate) {
        this.type = type;
        this.status = status;
        this.originalCountry = originalCountry;
        this.originalLanguage = originalLanguage;
        this.releaseDate = releaseDate;
    }
}
