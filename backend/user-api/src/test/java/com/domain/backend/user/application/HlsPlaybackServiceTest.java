package com.domain.backend.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.domain.backend.media.domain.MediaPackage;
import com.domain.backend.media.infrastructure.persistence.MediaPackageRepository;
import com.domain.backend.user.domain.PlaybackSession;
import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class HlsPlaybackServiceTest {

    @Test
    void rewritesManifestToSignedSegmentUrls() throws Exception {
        var playbackService = mock(PlaybackService.class);
        var packageRepository = mock(MediaPackageRepository.class);
        var storageClient = mock(ObjectStorageClient.class);
        HlsPlaybackService service = new HlsPlaybackService(playbackService, packageRepository, storageClient);

        PlaybackSession session = new PlaybackSession("session-1", 7L, 100L, 200L, Instant.now().plusSeconds(60));
        MediaPackage mediaPackage = new MediaPackage(100L, 10L, "hls-720p-v1", "videos/100/packages/pkg",
                "videos/100/packages/pkg/master.m3u8", 120_000);
        ReflectionTestUtils.setField(mediaPackage, "id", 200L);
        when(playbackService.requirePlaybackSession("session-1", new UserPrincipal(7L, "user", "User"))).thenReturn(session);
        when(packageRepository.findById(200L)).thenReturn(Optional.of(mediaPackage));
        when(storageClient.readObjectAsString("videos/100/packages/pkg/720p/index.m3u8"))
                .thenReturn("#EXTM3U\n#EXTINF:4,\nsegment000001.ts\n");
        when(storageClient.presignGetObject("videos/100/packages/pkg/720p/segment000001.ts"))
                .thenReturn(URI.create("http://localhost:9000/ott-originals/segment000001.ts?X-Amz-Signature=abc").toURL());

        var response = service.serve("session-1", "720p/index.m3u8", new UserPrincipal(7L, "user", "User"));

        assertThat(response.getBody().toString()).contains("X-Amz-Signature=abc");
    }

    @Test
    void rejectsSegmentProxyRequests() {
        var playbackService = mock(PlaybackService.class);
        var packageRepository = mock(MediaPackageRepository.class);
        var service = new HlsPlaybackService(playbackService, packageRepository, mock(ObjectStorageClient.class));
        PlaybackSession session = new PlaybackSession("session-1", 7L, 100L, 200L, Instant.now().plusSeconds(60));
        MediaPackage mediaPackage = new MediaPackage(100L, 10L, "hls-720p-v1", "videos/100/packages/pkg",
                "videos/100/packages/pkg/master.m3u8", 120_000);
        when(playbackService.requirePlaybackSession("session-1", new UserPrincipal(7L, "user", "User"))).thenReturn(session);
        when(packageRepository.findById(200L)).thenReturn(Optional.of(mediaPackage));

        assertThatThrownBy(() -> service.serve("session-1", "720p/segment000001.ts", new UserPrincipal(7L, "user", "User")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
