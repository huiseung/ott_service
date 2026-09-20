package com.domain.backend.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String sessionTokenHash;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant invalidatedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastUsedAt;

    protected UserSession() {
    }

    public UserSession(String sessionTokenHash, Long userId, Instant expiresAt) {
        Instant now = Instant.now();
        this.sessionTokenHash = sessionTokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.lastUsedAt = now;
    }

    public Long getId() { return id; }
    public String getSessionTokenHash() { return sessionTokenHash; }
    public Long getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getInvalidatedAt() { return invalidatedAt; }
}
