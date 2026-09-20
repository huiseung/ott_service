package com.domain.backend.video.infrastructure.storage;

import java.util.function.Function;

/** Rewrites trusted package playlists; only manifest requests return to the API. */
public final class SignedHlsManifest {

    private SignedHlsManifest() {
    }

    public static String rewrite(String manifest, String currentPath, String rootKey,
                                 ObjectStorageClient storageClient, Function<String, String> manifestUrl) {
        int slash = currentPath.lastIndexOf('/');
        String directory = slash < 0 ? "" : currentPath.substring(0, slash + 1);
        StringBuilder result = new StringBuilder();
        for (String line : manifest.split("\\R", -1)) {
            if (line.isBlank()) {
                result.append('\n');
            } else if (line.startsWith("#")) {
                // Current FFmpeg profile produces no URI-bearing tags. Fail closed if that changes.
                if (line.contains("URI=")) {
                    throw new IllegalArgumentException("Unsupported HLS URI tag");
                }
                result.append(line).append('\n');
            } else {
                String path = safePath(directory + line.trim());
                result.append(path.endsWith(".m3u8")
                        ? manifestUrl.apply(path)
                        : storageClient.presignGetObject(rootKey + "/" + path))
                        .append('\n');
            }
        }
        return result.toString();
    }

    public static String safePath(String path) {
        if (path.isBlank() || path.startsWith("/") || path.contains("..") || path.contains("//")
                || !path.matches("[A-Za-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid HLS path");
        }
        return path;
    }
}
