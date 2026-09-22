package com.domain.backend.content.infrastructure.persistence;

import com.domain.backend.content.domain.Genre;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenreRepository extends JpaRepository<Genre, Long> {

    List<Genre> findByCodeIn(Collection<String> codes);
}
