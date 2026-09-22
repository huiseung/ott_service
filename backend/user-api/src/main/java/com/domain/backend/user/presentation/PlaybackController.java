package com.domain.backend.user.presentation;

import com.domain.backend.user.application.HlsPlaybackService;
import com.domain.backend.user.application.PlaybackDtos.PlaybackStartResponse;
import com.domain.backend.user.application.PlaybackDtos.WatchEventRequest;
import com.domain.backend.user.application.PlaybackDtos.WatchEventResponse;
import com.domain.backend.user.application.PlaybackService;
import com.domain.backend.user.application.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/playback")
public class PlaybackController {

    private final PlaybackService playbackService;
    private final HlsPlaybackService hlsPlaybackService;

    public PlaybackController(PlaybackService playbackService, HlsPlaybackService hlsPlaybackService) {
        this.playbackService = playbackService;
        this.hlsPlaybackService = hlsPlaybackService;
    }

    @PostMapping("/videos/{videoId}/sessions")
    public PlaybackStartResponse startPlayback(@PathVariable Long videoId, @AuthenticationPrincipal UserPrincipal principal) {
        return playbackService.start(videoId, principal);
    }

    @PostMapping("/sessions/{sessionToken}/events")
    public WatchEventResponse publishEvent(
            @PathVariable String sessionToken,
            @Valid @RequestBody WatchEventRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return playbackService.publishEvent(sessionToken, request, principal);
    }

    @GetMapping("/sessions/{sessionToken}/hls/**")
    public ResponseEntity<?> hls(
            @PathVariable String sessionToken,
            HttpServletRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String marker = "/api/playback/sessions/" + sessionToken + "/hls/";
        String uri = request.getRequestURI();
        String hlsPath = uri.substring(uri.indexOf(marker) + marker.length());
        return hlsPlaybackService.serve(sessionToken, hlsPath, principal);
    }
}
