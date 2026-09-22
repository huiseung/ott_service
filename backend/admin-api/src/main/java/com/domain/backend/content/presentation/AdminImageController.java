package com.domain.backend.content.presentation;

import com.domain.backend.content.application.AdminImageDtos.ContentImageResponse;
import com.domain.backend.content.application.AdminImageDtos.ContentImagesResponse;
import com.domain.backend.content.application.AdminImageDtos.EpisodeImageResponse;
import com.domain.backend.content.application.AdminImageDtos.EpisodeImagesResponse;
import com.domain.backend.content.application.AdminImageService;
import com.domain.backend.content.domain.ContentImageType;
import com.domain.backend.content.domain.EpisodeImageType;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminImageController {

    private final AdminImageService imageService;

    public AdminImageController(AdminImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping("/contents/{contentId}/images")
    public ContentImagesResponse listContentImages(@PathVariable Long contentId) {
        return imageService.listContentImages(contentId);
    }

    @PutMapping(value = "/contents/{contentId}/images/{imageType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContentImageResponse uploadContentImage(
            @PathVariable Long contentId,
            @PathVariable ContentImageType imageType,
            @RequestPart("file") MultipartFile file
    ) {
        return imageService.uploadContentImage(contentId, imageType, file);
    }

    @DeleteMapping("/contents/{contentId}/images/{imageType}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteContentImage(@PathVariable Long contentId, @PathVariable ContentImageType imageType) {
        imageService.deleteContentImage(contentId, imageType);
    }

    @GetMapping("/episodes/{episodeId}/images")
    public EpisodeImagesResponse listEpisodeImages(@PathVariable Long episodeId) {
        return imageService.listEpisodeImages(episodeId);
    }

    @PutMapping(value = "/episodes/{episodeId}/images/{imageType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EpisodeImageResponse uploadEpisodeImage(
            @PathVariable Long episodeId,
            @PathVariable EpisodeImageType imageType,
            @RequestPart("file") MultipartFile file
    ) {
        return imageService.uploadEpisodeImage(episodeId, imageType, file);
    }

    @DeleteMapping("/episodes/{episodeId}/images/{imageType}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEpisodeImage(@PathVariable Long episodeId, @PathVariable EpisodeImageType imageType) {
        imageService.deleteEpisodeImage(episodeId, imageType);
    }
}
