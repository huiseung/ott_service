package com.domain.backend.worker.process;

public record MediaProbeResult(
        long durationMs,
        String videoCodec,
        String audioCodec,
        int width,
        int height,
        double fps
) {
}
