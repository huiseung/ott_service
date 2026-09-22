package com.domain.backend.content.infrastructure.persistence;

import com.domain.backend.content.domain.Content;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRepository extends JpaRepository<Content, Long> {
}
