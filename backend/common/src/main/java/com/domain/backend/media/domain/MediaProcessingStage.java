package com.domain.backend.media.domain;

public enum MediaProcessingStage {
    WAITING,
    DOWNLOADING_SOURCE,
    PROBING,
    TRANSCODING,
    UPLOADING_PACKAGE,
    VALIDATING_PACKAGE,
    PUBLISHING,
    COMPLETED
}
