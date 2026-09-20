package com.domain.backend.media.presentation;

import com.domain.backend.media.application.AdminPlaybackDtos.AdminPreviewStartResponse;
import com.domain.backend.media.application.AdminPreviewPlaybackService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/videos/{videoId}/preview")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPreviewPlaybackController {

    private final AdminPreviewPlaybackService playbackService;

    public AdminPreviewPlaybackController(AdminPreviewPlaybackService playbackService) {
        this.playbackService = playbackService;
    }

    @PostMapping
    public AdminPreviewStartResponse start(@PathVariable Long videoId) {
        return playbackService.start(videoId);
    }

    @GetMapping("/hls/**")
    public ResponseEntity<?> hls(@PathVariable Long videoId, HttpServletRequest request) {
        String marker = "/api/admin/videos/" + videoId + "/preview/hls/";
        String uri = request.getRequestURI();
        String hlsPath = uri.substring(uri.indexOf(marker) + marker.length());
        return playbackService.serve(videoId, hlsPath);
    }
}
