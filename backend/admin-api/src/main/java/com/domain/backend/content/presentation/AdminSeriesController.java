package com.domain.backend.content.presentation;

import com.domain.backend.content.application.AdminSeriesDtos.AttachVideoRequest;
import com.domain.backend.content.application.AdminSeriesDtos.EpisodeLocalizationRequest;
import com.domain.backend.content.application.AdminSeriesDtos.EpisodeLocalizationUpdateRequest;
import com.domain.backend.content.application.AdminSeriesDtos.EpisodeRequest;
import com.domain.backend.content.application.AdminSeriesDtos.EpisodeResponse;
import com.domain.backend.content.application.AdminSeriesDtos.MediaVersionRequest;
import com.domain.backend.content.application.AdminSeriesDtos.MediaVersionResponse;
import com.domain.backend.content.application.AdminSeriesDtos.SeasonRequest;
import com.domain.backend.content.application.AdminSeriesDtos.SeasonResponse;
import com.domain.backend.content.application.AdminSeriesService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSeriesController {

    private final AdminSeriesService seriesService;

    public AdminSeriesController(AdminSeriesService seriesService) {
        this.seriesService = seriesService;
    }

    @PostMapping("/contents/{contentId}/seasons")
    public ResponseEntity<SeasonResponse> createSeason(
            @PathVariable Long contentId,
            @Valid @RequestBody SeasonRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(seriesService.createSeason(contentId, request));
    }

    @GetMapping("/contents/{contentId}/seasons")
    public List<SeasonResponse> listSeasons(@PathVariable Long contentId) {
        return seriesService.listSeasons(contentId);
    }

    @GetMapping("/seasons/{seasonId}")
    public SeasonResponse getSeason(@PathVariable Long seasonId) {
        return seriesService.getSeason(seasonId);
    }

    @PatchMapping("/seasons/{seasonId}")
    public SeasonResponse updateSeason(@PathVariable Long seasonId, @Valid @RequestBody SeasonRequest request) {
        return seriesService.updateSeason(seasonId, request);
    }

    @PostMapping("/seasons/{seasonId}/episodes")
    public ResponseEntity<EpisodeResponse> createEpisode(
            @PathVariable Long seasonId,
            @Valid @RequestBody EpisodeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(seriesService.createEpisode(seasonId, request));
    }

    @GetMapping("/seasons/{seasonId}/episodes")
    public List<EpisodeResponse> listEpisodes(@PathVariable Long seasonId) {
        return seriesService.listEpisodes(seasonId);
    }

    @GetMapping("/episodes/{episodeId}")
    public EpisodeResponse getEpisode(@PathVariable Long episodeId) {
        return seriesService.getEpisode(episodeId);
    }

    @PatchMapping("/episodes/{episodeId}")
    public EpisodeResponse updateEpisode(@PathVariable Long episodeId, @Valid @RequestBody EpisodeRequest request) {
        return seriesService.updateEpisode(episodeId, request);
    }

    @PostMapping("/episodes/{episodeId}/localizations")
    public EpisodeResponse addEpisodeLocalization(
            @PathVariable Long episodeId,
            @Valid @RequestBody EpisodeLocalizationRequest request
    ) {
        return seriesService.addEpisodeLocalization(episodeId, request);
    }

    @PutMapping("/episodes/{episodeId}/localizations/{locale}")
    public EpisodeResponse updateEpisodeLocalization(
            @PathVariable Long episodeId,
            @PathVariable String locale,
            @Valid @RequestBody EpisodeLocalizationUpdateRequest request
    ) {
        return seriesService.updateEpisodeLocalization(episodeId, locale, request);
    }

    @DeleteMapping("/episodes/{episodeId}/localizations/{locale}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEpisodeLocalization(@PathVariable Long episodeId, @PathVariable String locale) {
        seriesService.deleteEpisodeLocalization(episodeId, locale);
    }

    @PostMapping("/contents/{contentId}/media-versions")
    public ResponseEntity<MediaVersionResponse> createContentMediaVersion(
            @PathVariable Long contentId,
            @Valid @RequestBody MediaVersionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(seriesService.createContentMediaVersion(contentId, request));
    }

    @GetMapping("/contents/{contentId}/media-versions")
    public List<MediaVersionResponse> listContentMediaVersions(@PathVariable Long contentId) {
        return seriesService.listContentMediaVersions(contentId);
    }

    @PostMapping("/episodes/{episodeId}/media-versions")
    public ResponseEntity<MediaVersionResponse> createEpisodeMediaVersion(
            @PathVariable Long episodeId,
            @Valid @RequestBody MediaVersionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(seriesService.createEpisodeMediaVersion(episodeId, request));
    }

    @GetMapping("/episodes/{episodeId}/media-versions")
    public List<MediaVersionResponse> listEpisodeMediaVersions(@PathVariable Long episodeId) {
        return seriesService.listEpisodeMediaVersions(episodeId);
    }

    @GetMapping("/media-versions/{mediaVersionId}")
    public MediaVersionResponse getMediaVersion(@PathVariable Long mediaVersionId) {
        return seriesService.getMediaVersion(mediaVersionId);
    }

    @PatchMapping("/media-versions/{mediaVersionId}")
    public MediaVersionResponse updateMediaVersion(
            @PathVariable Long mediaVersionId,
            @Valid @RequestBody MediaVersionRequest request
    ) {
        return seriesService.updateMediaVersion(mediaVersionId, request);
    }

    @PutMapping("/media-versions/{mediaVersionId}/video")
    public MediaVersionResponse attachVideo(
            @PathVariable Long mediaVersionId,
            @Valid @RequestBody AttachVideoRequest request
    ) {
        return seriesService.attachVideo(mediaVersionId, request);
    }
}
