package com.domain.backend.video.infrastructure.storage;

public record ListedPart(int partNumber, String etag, long size) {
}
