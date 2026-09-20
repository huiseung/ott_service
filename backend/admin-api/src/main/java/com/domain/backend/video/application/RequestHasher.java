package com.domain.backend.video.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class RequestHasher {

    public String createHash(VideoUploadDtos.CreateVideoRequest request) {
        String canonical = request.title() + "\n"
                + request.file().fileName() + "\n"
                + request.file().fileSize() + "\n"
                + request.file().contentType() + "\n"
                + request.file().fingerprint();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
