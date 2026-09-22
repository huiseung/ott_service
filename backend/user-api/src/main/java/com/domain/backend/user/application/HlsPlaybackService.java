package com.domain.backend.user.application;

import com.domain.backend.media.infrastructure.persistence.MediaPackageRepository;
import com.domain.backend.user.domain.PlaybackSession;
import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
import com.domain.backend.video.infrastructure.storage.SignedHlsManifest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HlsPlaybackService {

    private final PlaybackService playbackService;
    private final MediaPackageRepository mediaPackageRepository;
    private final ObjectStorageClient storageClient;

    public HlsPlaybackService(PlaybackService playbackService, MediaPackageRepository mediaPackageRepository,
                              ObjectStorageClient storageClient) {
        this.playbackService = playbackService;
        this.mediaPackageRepository = mediaPackageRepository;
        this.storageClient = storageClient;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> serve(String sessionToken, String hlsPath, UserPrincipal principal) {
        PlaybackSession playbackSession = playbackService.requirePlaybackSession(sessionToken, principal);
        var mediaPackage = mediaPackageRepository.findById(playbackSession.getMediaPackageId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media package not found"));
        String normalizedPath;
        try {
            normalizedPath = SignedHlsManifest.safePath(hlsPath);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid HLS path");
        }
        if (!normalizedPath.endsWith(".m3u8")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Use signed media URLs from the playlist");
        }
        String objectKey = "master.m3u8".equals(normalizedPath)
                ? mediaPackage.getMasterManifestKey()
                : mediaPackage.getRootKey() + "/" + normalizedPath;
        String manifest = storageClient.readObjectAsString(objectKey);
        String rewritten = SignedHlsManifest.rewrite(manifest, normalizedPath, mediaPackage.getRootKey(),
                storageClient, path -> "/api/playback/sessions/%s/hls/%s".formatted(sessionToken, path));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                .body(rewritten);
    }
}
