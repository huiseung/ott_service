package com.domain.backend.user.application;

import com.domain.backend.media.domain.MediaPackageStatus;
import com.domain.backend.media.infrastructure.persistence.MediaPackageRepository;
import com.domain.backend.playback.domain.WatchEventType;
import com.domain.backend.playback.event.WatchEvent;
import com.domain.backend.user.application.PlaybackDtos.PlaybackStartResponse;
import com.domain.backend.user.application.PlaybackDtos.WatchEventRequest;
import com.domain.backend.user.application.PlaybackDtos.WatchEventResponse;
import com.domain.backend.user.domain.PlaybackSession;
import com.domain.backend.user.infrastructure.persistence.PlaybackSessionRepository;
import com.domain.backend.video.domain.VideoStatus;
import com.domain.backend.video.infrastructure.persistence.VideoRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlaybackService {

    private final VideoRepository videoRepository;
    private final MediaPackageRepository mediaPackageRepository;
    private final PlaybackSessionRepository playbackSessionRepository;
    private final PlaybackResumeService resumeService;
    private final WatchEventPublisher eventPublisher;
    private final UserSecurityProperties properties;

    public PlaybackService(VideoRepository videoRepository, MediaPackageRepository mediaPackageRepository,
                           PlaybackSessionRepository playbackSessionRepository,
                           PlaybackResumeService resumeService, WatchEventPublisher eventPublisher,
                           UserSecurityProperties properties) {
        this.videoRepository = videoRepository;
        this.mediaPackageRepository = mediaPackageRepository;
        this.playbackSessionRepository = playbackSessionRepository;
        this.resumeService = resumeService;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    @Transactional
    public PlaybackStartResponse start(Long videoId, UserPrincipal principal) {
        var video = videoRepository.findById(videoId)
                .filter(candidate -> candidate.getStatus() == VideoStatus.READY && candidate.getPublishedMediaPackageId() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playable video not found"));
        var mediaPackage = mediaPackageRepository.findById(video.getPublishedMediaPackageId())
                .filter(candidate -> candidate.getStatus() == MediaPackageStatus.READY)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playable video not found"));
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(properties.getPlaybackSessionTtl());
        var playbackSession = playbackSessionRepository.save(new PlaybackSession(
                token,
                principal.userId(),
                video.getId(),
                mediaPackage.getId(),
                expiresAt
        ));
        long durationSeconds = mediaPackage.getDurationMs() / 1000;
        ResumeProgress resume = resumeService.getResumeProgress(principal.userId(), videoId, durationSeconds);
        return new PlaybackStartResponse(
                video.getId(),
                mediaPackage.getId(),
                playbackSession.getSessionToken(),
                "/api/playback/sessions/%s/hls/master.m3u8".formatted(token),
                durationSeconds,
                Math.min(resume.positionSeconds(), durationSeconds),
                expiresAt
        );
    }

    @Transactional(readOnly = true)
    public WatchEventResponse publishEvent(String playbackSessionToken, WatchEventRequest request, UserPrincipal principal) {
        var playbackSession = playbackSessionRepository.findBySessionTokenAndExpiresAtAfter(playbackSessionToken, Instant.now())
                .filter(session -> session.getUserId().equals(principal.userId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Playback session is not accessible"));
        var mediaPackage = mediaPackageRepository.findById(playbackSession.getMediaPackageId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media package not found"));
        long durationSeconds = mediaPackage.getDurationMs() / 1000;
        if (request.positionSeconds() > durationSeconds) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Position exceeds duration");
        }
        Instant occurredAt = request.occurredAt() == null ? Instant.now() : request.occurredAt();
        WatchEvent event = new WatchEvent(
                UUID.randomUUID(),
                request.eventType(),
                playbackSession.getSessionToken(),
                playbackSession.getId(),
                principal.userId(),
                playbackSession.getVideoId(),
                mediaPackage.getId(),
                normalizedPosition(request.eventType(), request.positionSeconds(), durationSeconds),
                durationSeconds,
                request.sequence(),
                occurredAt,
                Instant.now()
        );
        UUID eventId = eventPublisher.publish(event);
        return new WatchEventResponse(eventId.toString(), "ACCEPTED");
    }

    public PlaybackSession requirePlaybackSession(String playbackSessionToken, UserPrincipal principal) {
        return playbackSessionRepository.findBySessionTokenAndExpiresAtAfter(playbackSessionToken, Instant.now())
                .filter(session -> session.getUserId().equals(principal.userId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Playback session is not accessible"));
    }

    private long normalizedPosition(WatchEventType eventType, long positionSeconds, long durationSeconds) {
        if (eventType == WatchEventType.COMPLETE) {
            return durationSeconds;
        }
        return positionSeconds;
    }
}
