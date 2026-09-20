package com.domain.backend.worker.job;

public record ClaimedMediaJob(
        Long id,
        Long videoId,
        Long videoFileId,
        String profileVersion,
        int attempt,
        int generation,
        String workerId
) {
}
