package com.domain.backend.video.application;

import com.domain.backend.video.infrastructure.storage.StorageProperties;
import org.springframework.stereotype.Component;

@Component
public class MultipartPartSizePolicy {

    private static final int S3_MAX_PARTS = 10_000;

    private final StorageProperties properties;

    public MultipartPartSizePolicy(StorageProperties properties) {
        this.properties = properties;
    }

    public Result calculate(long fileSize) {
        long min = properties.getMinimumPartSize().toBytes();
        long max = properties.getMaximumPartSize().toBytes();
        if (min <= 0 || max < min) {
            throw new IllegalStateException("Invalid multipart part size configuration");
        }

        long required = (long) Math.ceil((double) fileSize / S3_MAX_PARTS);
        long selected = Math.max(min, required);
        selected = Math.min(max, selected);

        int totalParts = (int) Math.ceil((double) fileSize / selected);
        if (totalParts > S3_MAX_PARTS) {
            throw new IllegalArgumentException("File is too large for configured multipart limits");
        }
        return new Result(selected, Math.max(totalParts, 1));
    }

    public record Result(long partSize, int totalParts) {
    }
}
