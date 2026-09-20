package com.domain.backend.user.application;

import com.domain.backend.media.domain.MediaPackageStatus;
import com.domain.backend.media.infrastructure.persistence.MediaPackageRepository;
import com.domain.backend.user.application.PlaybackDtos.PlaybackStartResponse;
import com.domain.backend.user.application.PlaybackDtos.ProgressRequest;
import com.domain.backend.user.application.PlaybackDtos.ProgressResponse;
import com.domain.backend.user.domain.PlaybackSession;
import com.domain.backend.user.infrastructure.persistence.PlaybackSessionRepository;
import com.domain.backend.user.infrastructure.persistence.WatchProgressRepository;
import com.domain.backend.video.domain.VideoStatus;
import com.domain.backend.video.infrastructure.persistence.VideoRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlaybackService {

    private final VideoRepository videoRepository;
    private final MediaPackageRepository mediaPackageRepository;
    private final PlaybackSessionRepository playbackSessionRepository;
    private final WatchProgressRepository progressRepository;
    private final UserSecurityProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public PlaybackService(VideoRepository videoRepository, MediaPackageRepository mediaPackageRepository,
                           PlaybackSessionRepository playbackSessionRepository,
                           WatchProgressRepository progressRepository,
                           UserSecurityProperties properties) {
        this.videoRepository = videoRepository;
        this.mediaPackageRepository = mediaPackageRepository;
        this.playbackSessionRepository = playbackSessionRepository;
        this.progressRepository = progressRepository;
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
        String token = randomToken();
        var playbackSession = playbackSessionRepository.save(new PlaybackSession(
                token,
                principal.userId(),
                video.getId(),
                mediaPackage.getId(),
                Instant.now().plus(properties.getPlaybackSessionTtl())
        ));
        long resumePositionMs = progressRepository.findByIdUserIdAndIdVideoId(principal.userId(), videoId)
                .map(progress -> progress.getPositionMs())
                .orElse(0L);
        return new PlaybackStartResponse(
                video.getId(),
                mediaPackage.getId(),
                token,
                "/api/user/playback-sessions/%s/hls/master.m3u8".formatted(token),
                mediaPackage.getDurationMs(),
                Math.min(resumePositionMs, mediaPackage.getDurationMs())
        );
    }

    @Transactional
    public ProgressResponse saveProgress(String playbackSessionToken, ProgressRequest request, UserPrincipal principal) {
        var playbackSession = playbackSessionRepository.findBySessionTokenAndExpiresAtAfter(playbackSessionToken, Instant.now())
                .filter(session -> session.getUserId().equals(principal.userId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Playback session is not accessible"));
        var mediaPackage = mediaPackageRepository.findById(playbackSession.getMediaPackageId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media package not found"));
        if (request.durationMs() != mediaPackage.getDurationMs()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duration does not match media package");
        }
        if (request.positionMs() > mediaPackage.getDurationMs()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Position exceeds duration");
        }
        progressRepository.upsertProgress(
                principal.userId(),
                playbackSession.getVideoId(),
                mediaPackage.getId(),
                playbackSession.getId(),
                request.positionMs(),
                request.durationMs(),
                request.clientEventSeq(),
                request.occurredAt(),
                Instant.now()
        );
        playbackSessionRepository.touch(playbackSession.getId(), Instant.now());
        var current = progressRepository.findByIdUserIdAndIdVideoId(principal.userId(), playbackSession.getVideoId())
                .orElseThrow();
        return new ProgressResponse(current.getPositionMs(), current.getClientEventSeq(), current.getOccurredAt());
    }

    public PlaybackSession requirePlaybackSession(String playbackSessionToken, UserPrincipal principal) {
        return playbackSessionRepository.findBySessionTokenAndExpiresAtAfter(playbackSessionToken, Instant.now())
                .filter(session -> session.getUserId().equals(principal.userId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Playback session is not accessible"));
    }

    private String randomToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
