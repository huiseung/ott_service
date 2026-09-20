package com.domain.backend.user.infrastructure.persistence;

import com.domain.backend.user.domain.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
