package com.domain.backend.media.application;

import com.domain.backend.media.application.AdminPlaybackDtos.AdminPreviewStartResponse;
import com.domain.backend.media.domain.MediaPackage;
import com.domain.backend.media.domain.MediaPackageStatus;
import com.domain.backend.media.infrastructure.persistence.MediaPackageRepository;
import com.domain.backend.video.domain.VideoStatus;
import com.domain.backend.video.infrastructure.persistence.VideoRepository;
import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
import com.domain.backend.video.infrastructure.storage.SignedHlsManifest;
import com.domain.backend.video.infrastructure.storage.StorageProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminPreviewPlaybackService {

    private final VideoRepository videoRepository;
    private final MediaPackageRepository mediaPackageRepository;
    private final ObjectStorageClient storageClient;
    private final StorageProperties storageProperties;

    public AdminPreviewPlaybackService(VideoRepository videoRepository, MediaPackageRepository mediaPackageRepository,
                                       ObjectStorageClient storageClient, StorageProperties storageProperties) {
        this.videoRepository = videoRepository;
        this.mediaPackageRepository = mediaPackageRepository;
        this.storageClient = storageClient;
        this.storageProperties = storageProperties;
    }

    @Transactional(readOnly = true)
    public AdminPreviewStartResponse start(Long videoId) {
        var mediaPackage = requirePlayableMediaPackage(videoId);
        return new AdminPreviewStartResponse(
                videoId,
                mediaPackage.getId(),
                "/api/admin/videos/%d/preview/hls/master.m3u8".formatted(videoId),
                mediaPackage.getDurationMs(),
                storageProperties.getPresignedUrlTtl().toSeconds()
        );
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> serve(Long videoId, String hlsPath) {
        var mediaPackage = requirePlayableMediaPackage(videoId);
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
                storageClient, path -> "/api/admin/videos/%d/preview/hls/%s".formatted(videoId, path));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                .body(rewritten);
    }

    private MediaPackage requirePlayableMediaPackage(Long videoId) {
        var video = videoRepository.findById(videoId)
                .filter(candidate -> candidate.getStatus() == VideoStatus.READY && candidate.getPublishedMediaPackageId() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playable video not found"));
        return mediaPackageRepository.findById(video.getPublishedMediaPackageId())
                .filter(candidate -> candidate.getStatus() == MediaPackageStatus.READY)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playable video not found"));
    }

}
