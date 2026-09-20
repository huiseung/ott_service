package com.domain.backend.user.infrastructure.persistence;

import com.domain.backend.user.domain.UserSession;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findBySessionTokenHashAndInvalidatedAtIsNullAndExpiresAtAfter(String hash, Instant now);

    @Modifying
    @Transactional
    @Query("update UserSession s set s.invalidatedAt = :now where s.sessionTokenHash = :hash and s.invalidatedAt is null")
    int invalidate(String hash, Instant now);

    @Modifying
    @Transactional
    @Query("update UserSession s set s.lastUsedAt = :now where s.id = :id")
    int touch(Long id, Instant now);
}
