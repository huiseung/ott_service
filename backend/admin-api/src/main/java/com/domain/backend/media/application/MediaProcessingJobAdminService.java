package com.domain.backend.media.application;

import com.domain.backend.media.infrastructure.persistence.MediaProcessingJobRepository;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MediaProcessingJobAdminService {

    private final MediaProcessingJobRepository jobRepository;

    public MediaProcessingJobAdminService(MediaProcessingJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public void retryFailedJob(Long jobId) {
        if (jobRepository.retryFailedJob(jobId, Instant.now()) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job cannot be manually retried");
        }
    }
}
