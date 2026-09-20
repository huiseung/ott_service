package com.domain.backend.video.infrastructure.storage;

public record ObjectMetadata(boolean exists, long contentLength) {

    public static ObjectMetadata missing() {
        return new ObjectMetadata(false, -1);
    }
}
