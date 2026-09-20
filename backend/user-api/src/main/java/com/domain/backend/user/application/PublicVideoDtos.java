package com.domain.backend.user.application;

import java.util.List;

public final class PublicVideoDtos {

    private PublicVideoDtos() {
    }

    public record VideoListResponse(List<VideoListItem> items, Long nextCursor, boolean hasNext) {
    }

    public record VideoListItem(Long videoId, String title) {
    }

    public record VideoDetailResponse(Long videoId, String title, Long durationMs) {
    }
}
