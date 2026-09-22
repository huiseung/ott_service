package com.domain.backend.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.domain.backend.content.application.AdminContentDtos.ContentRequest;
import com.domain.backend.content.application.AdminSeriesDtos.EpisodeRequest;
import com.domain.backend.content.application.AdminSeriesDtos.SeasonRequest;
import com.domain.backend.content.domain.ContentImageType;
import com.domain.backend.content.domain.ContentStatus;
import com.domain.backend.content.domain.ContentType;
import com.domain.backend.content.domain.EpisodeImageType;
import com.domain.backend.content.infrastructure.persistence.ContentImageRepository;
import com.domain.backend.content.infrastructure.persistence.EpisodeImageRepository;
import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class AdminImageServiceIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ott_service")
            .withUsername("ott")
            .withPassword("ott");

    @Autowired
    AdminImageService imageService;

    @Autowired
    AdminContentService contentService;

    @Autowired
    AdminSeriesService seriesService;

    @Autowired
    ContentImageRepository contentImageRepository;

    @Autowired
    EpisodeImageRepository episodeImageRepository;

    @MockitoBean
    ObjectStorageClient storageClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("app.security.admin-password", () -> "test-admin-password");
        registry.add("app.storage.bucket", () -> "ott-originals-test");
        registry.add("app.storage.access-key", () -> "minio");
        registry.add("app.storage.secret-key", () -> "password");
        registry.add("app.images.max-file-size", () -> "10MB");
        registry.add("app.images.aspect-ratio-tolerance", () -> "0.03");
    }

    @Test
    void uploadsReplacesListsAndDeletesContentArtwork() throws Exception {
        List<String> uploaded = new ArrayList<>();
        doAnswer(invocation -> {
            String objectKey = invocation.getArgument(0);
            Path path = invocation.getArgument(1);
            uploaded.add(objectKey);
            assertThat(Files.size(path)).isPositive();
            return null;
        }).when(storageClient).putObject(any(), any(), any());
        when(storageClient.presignGetObject(any())).thenAnswer(invocation ->
                URI.create("https://storage.example/" + invocation.getArgument(0)).toURL());

        Long contentId = movie();
        var poster = imageService.uploadContentImage(contentId, ContentImageType.POSTER,
                multipart("poster.jpg", "image/jpeg", imageBytes("jpg", 600, 900)));
        var landscape = imageService.uploadContentImage(contentId, ContentImageType.LANDSCAPE,
                multipart("landscape.png", "image/png", imageBytes("png", 1280, 720)));
        var hero = imageService.uploadContentImage(contentId, ContentImageType.HERO,
                multipart("hero.webp", "image/webp", webpVp8x(1920, 1080)));

        assertThat(poster.width()).isEqualTo(600);
        assertThat(landscape.mimeType()).isEqualTo("image/png");
        assertThat(hero.height()).isEqualTo(1080);
        assertThat(uploaded).allMatch(key -> key.startsWith("contents/" + contentId + "/"));
        assertThat(contentImageRepository.findByContentIdOrderByImageTypeAsc(contentId))
                .extracting("objectKey")
                .noneMatch(value -> value.toString().startsWith("http"));

        String oldPosterKey = contentImageRepository.findByContentIdAndImageType(contentId, ContentImageType.POSTER).orElseThrow().getObjectKey();
        imageService.uploadContentImage(contentId, ContentImageType.POSTER,
                multipart("poster2.jpg", "image/jpeg", imageBytes("jpg", 800, 1200)));
        verify(storageClient).deleteObjects(List.of(oldPosterKey));
        assertThat(contentImageRepository.findByContentIdOrderByImageTypeAsc(contentId)).hasSize(3);

        assertThat(imageService.listContentImages(contentId).images()).hasSize(3);
        String replacementPosterKey = contentImageRepository.findByContentIdAndImageType(contentId, ContentImageType.POSTER).orElseThrow().getObjectKey();
        imageService.deleteContentImage(contentId, ContentImageType.POSTER);
        verify(storageClient).deleteObjects(List.of(replacementPosterKey));
        assertThat(contentImageRepository.findByContentIdAndImageType(contentId, ContentImageType.POSTER)).isEmpty();
    }

    @Test
    void uploadsReplacesAndDeletesEpisodeThumbnail() throws Exception {
        when(storageClient.presignGetObject(any())).thenAnswer(invocation ->
                URI.create("https://storage.example/" + invocation.getArgument(0)).toURL());
        Long episodeId = episode();

        var thumbnail = imageService.uploadEpisodeImage(episodeId, EpisodeImageType.THUMBNAIL,
                multipart("thumb.jpg", "image/jpeg", imageBytes("jpg", 1280, 720)));
        assertThat(thumbnail.width()).isEqualTo(1280);
        String oldKey = episodeImageRepository.findByEpisodeIdAndImageType(episodeId, EpisodeImageType.THUMBNAIL).orElseThrow().getObjectKey();
        assertThat(oldKey).startsWith("episodes/" + episodeId + "/thumbnail/");

        imageService.uploadEpisodeImage(episodeId, EpisodeImageType.THUMBNAIL,
                multipart("thumb.png", "image/png", imageBytes("png", 1920, 1080)));
        verify(storageClient).deleteObjects(List.of(oldKey));

        imageService.deleteEpisodeImage(episodeId, EpisodeImageType.THUMBNAIL);
        assertThat(episodeImageRepository.findByEpisodeIdAndImageType(episodeId, EpisodeImageType.THUMBNAIL)).isEmpty();
    }

    @Test
    void rejectsInvalidFilesAndMissingParents() throws Exception {
        Long contentId = movie();
        assertThatThrownBy(() -> imageService.uploadContentImage(999_999L, ContentImageType.POSTER,
                multipart("poster.jpg", "image/jpeg", imageBytes("jpg", 600, 900))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThatThrownBy(() -> imageService.uploadContentImage(contentId, ContentImageType.POSTER,
                multipart("bad.svg", "image/svg+xml", "<svg/>".getBytes())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> imageService.uploadContentImage(contentId, ContentImageType.POSTER,
                multipart("bad.jpg", "image/jpeg", "not image".getBytes())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> imageService.uploadContentImage(contentId, ContentImageType.POSTER,
                multipart("small.jpg", "image/jpeg", imageBytes("jpg", 200, 300))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> imageService.uploadContentImage(contentId, ContentImageType.LANDSCAPE,
                multipart("wrong.jpg", "image/jpeg", imageBytes("jpg", 1000, 1000))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> imageService.uploadEpisodeImage(999_999L, EpisodeImageType.THUMBNAIL,
                multipart("thumb.jpg", "image/jpeg", imageBytes("jpg", 1280, 720))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    private Long movie() {
        return contentService.create(new ContentRequest(ContentType.MOVIE, ContentStatus.DRAFT, "KR", "ko",
                LocalDate.of(2026, 1, 1))).id();
    }

    private Long episode() {
        Long seriesId = contentService.create(new ContentRequest(ContentType.SERIES, ContentStatus.DRAFT, "KR", "ko",
                LocalDate.of(2026, 1, 1))).id();
        Long seasonId = seriesService.createSeason(seriesId, new SeasonRequest(1, ContentStatus.DRAFT)).id();
        return seriesService.createEpisode(seasonId, new EpisodeRequest(1, ContentStatus.DRAFT, null)).id();
    }

    private MockMultipartFile multipart(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", name, contentType, bytes);
    }

    private byte[] imageBytes(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private byte[] webpVp8x(int width, int height) {
        byte[] bytes = new byte[30];
        putAscii(bytes, 0, "RIFF");
        putAscii(bytes, 8, "WEBP");
        putAscii(bytes, 12, "VP8X");
        int storedWidth = width - 1;
        int storedHeight = height - 1;
        bytes[24] = (byte) (storedWidth & 0xff);
        bytes[25] = (byte) ((storedWidth >> 8) & 0xff);
        bytes[26] = (byte) ((storedWidth >> 16) & 0xff);
        bytes[27] = (byte) (storedHeight & 0xff);
        bytes[28] = (byte) ((storedHeight >> 8) & 0xff);
        bytes[29] = (byte) ((storedHeight >> 16) & 0xff);
        return bytes;
    }

    private void putAscii(byte[] bytes, int offset, String value) {
        byte[] ascii = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(ascii, 0, bytes, offset, ascii.length);
    }
}
