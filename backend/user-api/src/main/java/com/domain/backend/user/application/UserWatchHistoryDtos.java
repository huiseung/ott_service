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
            long positionMs,
            long durationMs,
            double progressRate,
            Instant watchedAt
    ) {
    }
}
