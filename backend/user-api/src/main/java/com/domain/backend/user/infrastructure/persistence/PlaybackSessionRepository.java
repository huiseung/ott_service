package com.domain.backend.user.infrastructure.persistence;

import com.domain.backend.user.domain.PlaybackSession;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PlaybackSessionRepository extends JpaRepository<PlaybackSession, Long> {

    Optional<PlaybackSession> findBySessionTokenAndExpiresAtAfter(String sessionToken, Instant now);

    @Modifying
    @Transactional
    @Query("update PlaybackSession s set s.lastAccessedAt = :now where s.id = :id")
    int touch(Long id, Instant now);
}
