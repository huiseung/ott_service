package com.domain.backend.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "genres")
public class Genre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private GenreStatus status;

    @Column(nullable = false)
    private int sortOrder;

    protected Genre() {
    }

    public Genre(String code, GenreStatus status, int sortOrder) {
        this.code = code;
        this.status = status;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public GenreStatus getStatus() { return status; }
    public int getSortOrder() { return sortOrder; }
}
