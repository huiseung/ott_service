package com.domain.backend.content.application;

import com.domain.backend.content.application.AdminImageDtos.ContentImageResponse;
import com.domain.backend.content.application.AdminImageDtos.ContentImagesResponse;
import com.domain.backend.content.application.AdminImageDtos.EpisodeImageResponse;
import com.domain.backend.content.application.AdminImageDtos.EpisodeImagesResponse;
import com.domain.backend.content.domain.ContentImage;
import com.domain.backend.content.domain.ContentImageType;
import com.domain.backend.content.domain.EpisodeImage;
import com.domain.backend.content.domain.EpisodeImageType;
import com.domain.backend.content.infrastructure.persistence.ContentImageRepository;
import com.domain.backend.content.infrastructure.persistence.ContentRepository;
import com.domain.backend.content.infrastructure.persistence.EpisodeImageRepository;
import com.domain.backend.content.infrastructure.persistence.EpisodeRepository;
import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminImageService {

    private static final List<String> ALLOWED_MIME_TYPES = List.of("image/jpeg", "image/png", "image/webp");

    private final ContentRepository contentRepository;
    private final EpisodeRepository episodeRepository;
    private final ContentImageRepository contentImageRepository;
    private final EpisodeImageRepository episodeImageRepository;
    private final ObjectStorageClient storageClient;
    private final ImageAssetProperties properties;

    public AdminImageService(ContentRepository contentRepository,
                             EpisodeRepository episodeRepository,
                             ContentImageRepository contentImageRepository,
                             EpisodeImageRepository episodeImageRepository,
                             ObjectStorageClient storageClient,
                             ImageAssetProperties properties) {
        this.contentRepository = contentRepository;
        this.episodeRepository = episodeRepository;
        this.contentImageRepository = contentImageRepository;
        this.episodeImageRepository = episodeImageRepository;
        this.storageClient = storageClient;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public ContentImagesResponse listContentImages(Long contentId) {
        requireContent(contentId);
        return new ContentImagesResponse(contentImageRepository.findByContentIdOrderByImageTypeAsc(contentId).stream()
                .map(this::contentImageResponse)
                .toList());
    }

    @Transactional
    public ContentImageResponse uploadContentImage(Long contentId, ContentImageType imageType, MultipartFile file) {
        requireContent(contentId);
        ImageUpload upload = validate(file, contentSpec(imageType));
        String objectKey = "contents/%d/%s/%s.%s".formatted(
                contentId,
                imageType.name().toLowerCase(Locale.ROOT),
                UUID.randomUUID(),
                extension(upload.mimeType())
        );
        String oldObjectKey = null;
        ContentImage image = contentImageRepository.findByContentIdAndImageType(contentId, imageType).orElse(null);
        if (image == null) {
            image = new ContentImage(contentId, imageType, objectKey, upload.width(), upload.height(), upload.fileSize(), upload.mimeType());
        } else {
            oldObjectKey = image.getObjectKey();
            image.replace(objectKey, upload.width(), upload.height(), upload.fileSize(), upload.mimeType());
        }
        putObject(objectKey, upload);
        ContentImage saved = contentImageRepository.save(image);
        // TODO: If DB commit fails after object upload, the newly uploaded object may be orphaned.
        deleteObjectAfterCommit(oldObjectKey);
        return contentImageResponse(saved);
    }

    @Transactional
    public void deleteContentImage(Long contentId, ContentImageType imageType) {
        requireContent(contentId);
        ContentImage image = contentImageRepository.findByContentIdAndImageType(contentId, imageType).orElse(null);
        if (image == null) {
            return;
        }
        contentImageRepository.deleteByContentIdAndImageType(contentId, imageType);
        deleteObjectAfterCommit(image.getObjectKey());
    }

    @Transactional(readOnly = true)
    public EpisodeImagesResponse listEpisodeImages(Long episodeId) {
        requireEpisode(episodeId);
        return new EpisodeImagesResponse(episodeImageRepository.findByEpisodeIdOrderByImageTypeAsc(episodeId).stream()
                .map(this::episodeImageResponse)
                .toList());
    }

    @Transactional
    public EpisodeImageResponse uploadEpisodeImage(Long episodeId, EpisodeImageType imageType, MultipartFile file) {
        requireEpisode(episodeId);
        ImageUpload upload = validate(file, episodeSpec(imageType));
        String objectKey = "episodes/%d/%s/%s.%s".formatted(
                episodeId,
                imageType.name().toLowerCase(Locale.ROOT),
                UUID.randomUUID(),
                extension(upload.mimeType())
        );
        String oldObjectKey = null;
        EpisodeImage image = episodeImageRepository.findByEpisodeIdAndImageType(episodeId, imageType).orElse(null);
        if (image == null) {
            image = new EpisodeImage(episodeId, imageType, objectKey, upload.width(), upload.height(), upload.fileSize(), upload.mimeType());
        } else {
            oldObjectKey = image.getObjectKey();
            image.replace(objectKey, upload.width(), upload.height(), upload.fileSize(), upload.mimeType());
        }
        putObject(objectKey, upload);
        EpisodeImage saved = episodeImageRepository.save(image);
        // TODO: Replace can leave a new orphan object if DB commit fails after upload.
        deleteObjectAfterCommit(oldObjectKey);
        return episodeImageResponse(saved);
    }

    @Transactional
    public void deleteEpisodeImage(Long episodeId, EpisodeImageType imageType) {
        requireEpisode(episodeId);
        EpisodeImage image = episodeImageRepository.findByEpisodeIdAndImageType(episodeId, imageType).orElse(null);
        if (image == null) {
            return;
        }
        episodeImageRepository.deleteByEpisodeIdAndImageType(episodeId, imageType);
        deleteObjectAfterCommit(image.getObjectKey());
    }

    private ImageUpload validate(MultipartFile file, ImageSpec spec) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        }
        if (file.getSize() > properties.getMaxFileSize().toBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is too large");
        }
        String declaredMimeType = file.getContentType();
        if (!ALLOWED_MIME_TYPES.contains(declaredMimeType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image MIME type");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file cannot be read", e);
        }
        ImageDimensions dimensions = dimensions(bytes, declaredMimeType);
        if (dimensions.width() < spec.minWidth() || dimensions.height() < spec.minHeight()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image resolution is too small");
        }
        double actualRatio = (double) dimensions.width() / dimensions.height();
        if (Math.abs(actualRatio - spec.expectedRatio()) > properties.getAspectRatioTolerance()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image aspect ratio is invalid");
        }
        return new ImageUpload(bytes, dimensions.width(), dimensions.height(), file.getSize(), declaredMimeType);
    }

    private ImageDimensions dimensions(byte[] bytes, String mimeType) {
        if ("image/webp".equals(mimeType)) {
            return webpDimensions(bytes);
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is not a decodable image");
            }
            return new ImageDimensions(image.getWidth(), image.getHeight());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is not a decodable image", e);
        }
    }

    private ImageDimensions webpDimensions(byte[] bytes) {
        if (bytes.length < 30 || !ascii(bytes, 0, 4).equals("RIFF") || !ascii(bytes, 8, 4).equals("WEBP")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is not a valid WebP image");
        }
        String chunk = ascii(bytes, 12, 4);
        if ("VP8X".equals(chunk)) {
            if (bytes.length < 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid WebP header");
            return new ImageDimensions(1 + uint24(bytes, 24), 1 + uint24(bytes, 27));
        }
        if ("VP8L".equals(chunk)) {
            if (bytes.length < 25) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid WebP header");
            int b0 = bytes[21] & 0xff;
            int b1 = bytes[22] & 0xff;
            int b2 = bytes[23] & 0xff;
            int b3 = bytes[24] & 0xff;
            int width = 1 + (((b1 & 0x3f) << 8) | b0);
            int height = 1 + (((b3 & 0x0f) << 10) | (b2 << 2) | ((b1 & 0xc0) >> 6));
            return new ImageDimensions(width, height);
        }
        if ("VP8 ".equals(chunk)) {
            if (bytes.length < 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid WebP header");
            int width = uint16(bytes, 26) & 0x3fff;
            int height = uint16(bytes, 28) & 0x3fff;
            return new ImageDimensions(width, height);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported WebP bitstream");
    }

    private void putObject(String objectKey, ImageUpload upload) {
        Path temp = null;
        try {
            temp = Files.createTempFile("ott-artwork-", ".upload");
            Files.write(temp, upload.bytes());
            storageClient.putObject(objectKey, temp, upload.mimeType());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store image", e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private ContentImageResponse contentImageResponse(ContentImage image) {
        return new ContentImageResponse(
                image.getImageType(),
                storageClient.presignGetObject(image.getObjectKey()).toString(),
                image.getWidth(),
                image.getHeight(),
                image.getFileSize(),
                image.getMimeType(),
                image.getCreatedAt(),
                image.getUpdatedAt()
        );
    }

    private EpisodeImageResponse episodeImageResponse(EpisodeImage image) {
        return new EpisodeImageResponse(
                image.getImageType(),
                storageClient.presignGetObject(image.getObjectKey()).toString(),
                image.getWidth(),
                image.getHeight(),
                image.getFileSize(),
                image.getMimeType(),
                image.getCreatedAt(),
                image.getUpdatedAt()
        );
    }

    private void requireContent(Long contentId) {
        if (!contentRepository.existsById(contentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
        }
    }

    private void requireEpisode(Long episodeId) {
        if (!episodeRepository.existsById(episodeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Episode not found");
        }
    }

    private void deleteObjectAfterCommit(String objectKey) {
        if (objectKey == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storageClient.deleteObjects(List.of(objectKey));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storageClient.deleteObjects(List.of(objectKey));
            }
        });
    }

    private ImageSpec contentSpec(ContentImageType imageType) {
        return switch (imageType) {
            case POSTER -> new ImageSpec(600, 900, 2.0 / 3.0);
            case LANDSCAPE -> new ImageSpec(1280, 720, 16.0 / 9.0);
            case HERO -> new ImageSpec(1920, 1080, 16.0 / 9.0);
        };
    }

    private ImageSpec episodeSpec(EpisodeImageType imageType) {
        return switch (imageType) {
            case THUMBNAIL -> new ImageSpec(1280, 720, 16.0 / 9.0);
        };
    }

    private String extension(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image MIME type");
        };
    }

    private String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private int uint16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private int uint24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8) | ((bytes[offset + 2] & 0xff) << 16);
    }

    private record ImageSpec(int minWidth, int minHeight, double expectedRatio) {
    }

    private record ImageDimensions(int width, int height) {
    }

    private record ImageUpload(byte[] bytes, int width, int height, long fileSize, String mimeType) {
    }
}
