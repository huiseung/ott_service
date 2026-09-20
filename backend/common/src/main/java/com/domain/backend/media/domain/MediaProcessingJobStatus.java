package com.domain.backend.media.domain;

public enum MediaProcessingJobStatus {
    QUEUED,
    PROCESSING,
    RETRY_WAIT,
    COMPLETED,
    FAILED,
    SUPERSEDED
}
