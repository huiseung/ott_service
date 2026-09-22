package com.domain.backend.user.application;

import java.time.Instant;
import java.util.List;

public final class UserWatchHistoryDtos {

    private UserWatchHistoryDtos() {
    }

    public record WatchHistoryResponse(List<WatchHistoryItem> items) {
    }

    public record WatchHistoryItem(
            Long videoId,
            String title,
            long positionSeconds,
            long durationSeconds,
            double progressRate,
            Instant watchedAt
    ) {
    }
}
