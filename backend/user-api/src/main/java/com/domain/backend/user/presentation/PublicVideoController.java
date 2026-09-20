package com.domain.backend.user.presentation;

import com.domain.backend.user.application.PublicVideoDtos.VideoDetailResponse;
import com.domain.backend.user.application.PublicVideoDtos.VideoListResponse;
import com.domain.backend.user.application.PublicVideoQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/videos")
public class PublicVideoController {

    private final PublicVideoQueryService queryService;

    public PublicVideoController(PublicVideoQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public VideoListResponse listVideos(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size
    ) {
        return queryService.listVideos(cursor, size);
    }

    @GetMapping("/{videoId}")
    public VideoDetailResponse getVideo(@PathVariable Long videoId) {
        return queryService.getVideo(videoId);
    }
}
