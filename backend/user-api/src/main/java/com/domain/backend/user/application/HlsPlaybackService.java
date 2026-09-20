package com.domain.backend.user.application;

import com.domain.backend.media.infrastructure.persistence.MediaPackageRepository;
import com.domain.backend.user.domain.PlaybackSession;
import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
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
    public ResponseEntity<?> serve(String sessionToken, String hlsPath, String rangeHeader, UserPrincipal principal) {
        PlaybackSession playbackSession = playbackService.requirePlaybackSession(sessionToken, principal);
        var mediaPackage = mediaPackageRepository.findById(playbackSession.getMediaPackageId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media package not found"));
        String normalizedPath = normalizePath(hlsPath);
        String objectKey = "master.m3u8".equals(normalizedPath)
                ? mediaPackage.getMasterManifestKey()
                : mediaPackage.getRootKey() + "/" + normalizedPath;
        if (normalizedPath.endsWith(".m3u8")) {
            String manifest = storageClient.readObjectAsString(objectKey);
            String rewritten = rewriteManifest(sessionToken, normalizedPath, manifest);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                    .body(rewritten);
        }
        return streamObject(objectKey, normalizedPath, rangeHeader);
    }

    private ResponseEntity<StreamingResponseBody> streamObject(String objectKey, String normalizedPath, String rangeHeader) {
        validateRange(rangeHeader);
        var object = storageClient.openObjectStream(objectKey, rangeHeader);
        StreamingResponseBody body = outputStream -> {
            try (object) {
                object.inputStream().transferTo(outputStream);
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to stream HLS object", e);
            }
        };
        var builder = ResponseEntity.status(object.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .contentType(mediaType(normalizedPath))
                .contentLength(object.contentLength())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes");
        if (object.contentRange() != null) {
            builder.header(HttpHeaders.CONTENT_RANGE, object.contentRange());
        }
        return builder.body(body);
    }

    private String rewriteManifest(String sessionToken, String currentPath, String manifest) {
        String currentDirectory = "";
        int slash = currentPath.lastIndexOf('/');
        if (slash >= 0) currentDirectory = currentPath.substring(0, slash + 1);
        StringBuilder rewritten = new StringBuilder();
        for (String line : manifest.split("\\R", -1)) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("http://") || line.startsWith("https://")) {
                rewritten.append(line).append('\n');
            } else {
                String childPath = line.contains("/") ? line : currentDirectory + line;
                rewritten.append("/api/user/playback-sessions/")
                        .append(sessionToken).append("/hls/").append(childPath).append('\n');
            }
        }
        return rewritten.toString();
    }

    private String normalizePath(String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid HLS path");
        }
        return normalized;
    }

    private void validateRange(String rangeHeader) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return;
        }
        if (!rangeHeader.matches("bytes=(\\d+-\\d*|\\d*-\\d+)")) {
            throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Invalid range");
        }
    }

    private MediaType mediaType(String path) {
        if (path.endsWith(".ts")) return MediaType.parseMediaType("video/mp2t");
        if (path.endsWith(".m4s") || path.endsWith(".mp4")) return MediaType.parseMediaType("video/mp4");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
