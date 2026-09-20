package com.domain.backend.video.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.domain.backend.video.infrastructure.storage.StorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class MultipartPartSizePolicyTest {

    @Test
    void calculatesPartSizeWithinConfiguredBounds() {
        StorageProperties properties = new StorageProperties();
        properties.setMinimumPartSize(DataSize.ofMegabytes(16));
        properties.setMaximumPartSize(DataSize.ofMegabytes(64));
        MultipartPartSizePolicy policy = new MultipartPartSizePolicy(properties);

        var result = policy.calculate(DataSize.ofGigabytes(8).toBytes());

        assertThat(result.partSize()).isBetween(DataSize.ofMegabytes(16).toBytes(), DataSize.ofMegabytes(64).toBytes());
        assertThat(result.totalParts()).isPositive();
    }
}
