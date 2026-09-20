package com.domain.backend.video.infrastructure.persistence;

import com.domain.backend.video.domain.UploadIdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadIdempotencyKeyRepository extends JpaRepository<UploadIdempotencyKey, String> {
}
