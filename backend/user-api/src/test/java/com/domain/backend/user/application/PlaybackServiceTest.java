package com.domain.backend.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.domain.backend.media.domain.MediaPackage;
import com.domain.backend.media.infrastructure.persistence.MediaPackageRepository;
import com.domain.backend.playback.domain.WatchEventType;
import com.domain.backend.playback.event.WatchEvent;
import com.domain.backend.user.application.PlaybackDtos.WatchEventRequest;
import com.domain.backend.user.domain.PlaybackSession;
import com.domain.backend.user.infrastructure.persistence.PlaybackSessionRepository;
import com.domain.backend.video.domain.Video;
import com.domain.backend.video.infrastructure.persistence.VideoRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PlaybackServiceTest {

    @Test
    void createsSessionWithResumePositionFromServerSidePrincipal() {
        var videoRepository = mock(VideoRepository.class);
        var mediaPackageRepository = mock(MediaPackageRepository.class);
        var sessionRepository = mock(PlaybackSessionRepository.class);
        var resumeService = mock(PlaybackResumeService.class);
        var eventPublisher = mock(WatchEventPublisher.class);
        var properties = new UserSecurityProperties();
        properties.setPlaybackSessionTtl(java.time.Duration.ofHours(6));
        PlaybackService service = new PlaybackService(videoRepository, mediaPackageRepository, sessionRepository,
                resumeService, eventPublisher, properties);

        Video video = readyVideo(100L, 200L);
        MediaPackage mediaPackage = mediaPackage(200L, 3_600_000L);
        when(videoRepository.findById(100L)).thenReturn(Optional.of(video));
        when(mediaPackageRepository.findById(200L)).thenReturn(Optional.of(mediaPackage));
        when(sessionRepository.save(any(PlaybackSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resumeService.getResumeProgress(7L, 100L, 3600L)).thenReturn(new ResumeProgress(1230, 3600));

        var response = service.start(100L, new UserPrincipal(7L, "user", "User"));

        assertThat(response.videoId()).isEqualTo(100L);
        assertThat(response.resumePositionSeconds()).isEqualTo(1230);
        assertThat(response.hlsUrl()).startsWith("/api/playback/sessions/");
    }

    @Test
    void publishesWatchEventUsingSessionValuesInsteadOfClientIdentity() {
        var videoRepository = mock(VideoRepository.class);
        var mediaPackageRepository = mock(MediaPackageRepository.class);
        var sessionRepository = mock(PlaybackSessionRepository.class);
        var resumeService = mock(PlaybackResumeService.class);
        var eventPublisher = mock(WatchEventPublisher.class);
        var properties = new UserSecurityProperties();
        PlaybackService service = new PlaybackService(videoRepository, mediaPackageRepository, sessionRepository,
                resumeService, eventPublisher, properties);

        PlaybackSession session = new PlaybackSession("session-1", 7L, 100L, 200L, Instant.now().plusSeconds(60));
        ReflectionTestUtils.setField(session, "id", 55L);
        when(sessionRepository.findBySessionTokenAndExpiresAtAfter(any(), any())).thenReturn(Optional.of(session));
        when(mediaPackageRepository.findById(200L)).thenReturn(Optional.of(mediaPackage(200L, 3_600_000L)));
        when(eventPublisher.publish(any(WatchEvent.class))).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        var response = service.publishEvent("session-1",
                new WatchEventRequest(WatchEventType.PROGRESS, 120, 12, Instant.parse("2026-09-21T00:00:00Z")),
                new UserPrincipal(7L, "user", "User"));

        ArgumentCaptor<WatchEvent> event = ArgumentCaptor.forClass(WatchEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(response.eventId()).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(event.getValue().userId()).isEqualTo(7L);
        assertThat(event.getValue().videoId()).isEqualTo(100L);
        assertThat(event.getValue().playbackSessionId()).isEqualTo("session-1");
        assertThat(event.getValue().sequence()).isEqualTo(12);
    }

    private Video readyVideo(Long id, Long packageId) {
        Video video = new Video("title");
        ReflectionTestUtils.setField(video, "id", id);
        video.publish(10L, packageId);
        return video;
    }

    private MediaPackage mediaPackage(Long id, long durationMs) {
        MediaPackage mediaPackage = new MediaPackage(100L, 10L, "hls-720p-v1", "videos/100/packages/pkg",
                "videos/100/packages/pkg/master.m3u8", durationMs);
        ReflectionTestUtils.setField(mediaPackage, "id", id);
        return mediaPackage;
    }
}
