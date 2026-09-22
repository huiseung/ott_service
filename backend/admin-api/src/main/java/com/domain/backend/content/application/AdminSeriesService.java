package com.domain.backend.content.application;

import com.domain.backend.content.application.AdminSeriesDtos.AttachVideoRequest;
import com.domain.backend.content.application.AdminSeriesDtos.EpisodeLocalizationRequest;
import com.domain.backend.content.application.AdminSeriesDtos.EpisodeLocalizationResponse;
import com.domain.backend.content.application.AdminSeriesDtos.EpisodeLocalizationUpdateRequest;
import com.domain.backend.content.application.AdminSeriesDtos.EpisodeRequest;
import com.domain.backend.content.application.AdminSeriesDtos.EpisodeResponse;
import com.domain.backend.content.application.AdminSeriesDtos.MediaVersionRequest;
import com.domain.backend.content.application.AdminSeriesDtos.MediaVersionResponse;
import com.domain.backend.content.application.AdminSeriesDtos.SeasonRequest;
import com.domain.backend.content.application.AdminSeriesDtos.SeasonResponse;
import com.domain.backend.content.application.AdminSeriesDtos.VideoSummary;
import com.domain.backend.content.domain.Content;
import com.domain.backend.content.domain.ContentStatus;
import com.domain.backend.content.domain.ContentType;
import com.domain.backend.content.domain.Episode;
import com.domain.backend.content.domain.EpisodeLocalization;
import com.domain.backend.content.domain.MediaVersion;
import com.domain.backend.content.domain.MediaVersionType;
import com.domain.backend.content.domain.Season;
import com.domain.backend.content.infrastructure.persistence.ContentRepository;
import com.domain.backend.content.infrastructure.persistence.EpisodeLocalizationRepository;
import com.domain.backend.content.infrastructure.persistence.EpisodeRepository;
import com.domain.backend.content.infrastructure.persistence.MediaVersionRepository;
import com.domain.backend.content.infrastructure.persistence.SeasonRepository;
import com.domain.backend.video.domain.Video;
import com.domain.backend.video.infrastructure.persistence.VideoRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminSeriesService {

    private final ContentRepository contentRepository;
    private final SeasonRepository seasonRepository;
    private final EpisodeRepository episodeRepository;
    private final EpisodeLocalizationRepository episodeLocalizationRepository;
    private final MediaVersionRepository mediaVersionRepository;
    private final VideoRepository videoRepository;

    public AdminSeriesService(ContentRepository contentRepository,
                              SeasonRepository seasonRepository,
                              EpisodeRepository episodeRepository,
                              EpisodeLocalizationRepository episodeLocalizationRepository,
                              MediaVersionRepository mediaVersionRepository,
                              VideoRepository videoRepository) {
        this.contentRepository = contentRepository;
        this.seasonRepository = seasonRepository;
        this.episodeRepository = episodeRepository;
        this.episodeLocalizationRepository = episodeLocalizationRepository;
        this.mediaVersionRepository = mediaVersionRepository;
        this.videoRepository = videoRepository;
    }

    @Transactional
    public SeasonResponse createSeason(Long contentId, SeasonRequest request) {
        Content content = requireContent(contentId);
        requireSeries(content);
        validatePositive(request.seasonNumber(), "seasonNumber");
        if (seasonRepository.existsBySeriesContentIdAndSeasonNumber(contentId, request.seasonNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Season number already exists");
        }
        return seasonResponse(seasonRepository.save(new Season(contentId, request.seasonNumber(), request.status())));
    }

    @Transactional(readOnly = true)
    public List<SeasonResponse> listSeasons(Long contentId) {
        Content content = requireContent(contentId);
        requireSeries(content);
        return seasonRepository.findBySeriesContentIdOrderBySeasonNumberAsc(contentId).stream()
                .map(this::seasonResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SeasonResponse getSeason(Long seasonId) {
        return seasonResponse(requireSeason(seasonId));
    }

    @Transactional
    public SeasonResponse updateSeason(Long seasonId, SeasonRequest request) {
        validatePositive(request.seasonNumber(), "seasonNumber");
        Season season = requireSeason(seasonId);
        if (season.getSeasonNumber() != request.seasonNumber()
                && seasonRepository.existsBySeriesContentIdAndSeasonNumber(season.getSeriesContentId(), request.seasonNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Season number already exists");
        }
        season.update(request.seasonNumber(), request.status());
        return seasonResponse(season);
    }

    @Transactional
    public EpisodeResponse createEpisode(Long seasonId, EpisodeRequest request) {
        requireSeason(seasonId);
        validatePositive(request.episodeNumber(), "episodeNumber");
        if (episodeRepository.existsBySeasonIdAndEpisodeNumber(seasonId, request.episodeNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Episode number already exists");
        }
        return episodeResponse(episodeRepository.save(new Episode(
                seasonId,
                request.episodeNumber(),
                request.status(),
                request.releaseAt()
        )));
    }

    @Transactional(readOnly = true)
    public List<EpisodeResponse> listEpisodes(Long seasonId) {
        requireSeason(seasonId);
        List<Episode> episodes = episodeRepository.findBySeasonIdOrderByEpisodeNumberAsc(seasonId);
        Map<Long, List<EpisodeLocalizationResponse>> localizationsByEpisodeId = localizationsByEpisodeId(
                episodes.stream().map(Episode::getId).toList()
        );
        return episodes.stream()
                .map(episode -> episodeResponse(episode, localizationsByEpisodeId.getOrDefault(episode.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public EpisodeResponse getEpisode(Long episodeId) {
        return episodeResponse(requireEpisode(episodeId));
    }

    @Transactional
    public EpisodeResponse updateEpisode(Long episodeId, EpisodeRequest request) {
        validatePositive(request.episodeNumber(), "episodeNumber");
        Episode episode = requireEpisode(episodeId);
        if (episode.getEpisodeNumber() != request.episodeNumber()
                && episodeRepository.existsBySeasonIdAndEpisodeNumber(episode.getSeasonId(), request.episodeNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Episode number already exists");
        }
        episode.update(request.episodeNumber(), request.status(), request.releaseAt());
        return episodeResponse(episode);
    }

    @Transactional
    public EpisodeResponse addEpisodeLocalization(Long episodeId, EpisodeLocalizationRequest request) {
        requireEpisode(episodeId);
        String locale = normalizeLocale(request.locale());
        if (episodeLocalizationRepository.existsByEpisodeIdAndLocale(episodeId, locale)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Episode localization already exists");
        }
        episodeLocalizationRepository.save(new EpisodeLocalization(
                episodeId,
                locale,
                request.title().trim(),
                trimToNull(request.description())
        ));
        return getEpisode(episodeId);
    }

    @Transactional
    public EpisodeResponse updateEpisodeLocalization(Long episodeId, String locale, EpisodeLocalizationUpdateRequest request) {
        requireEpisode(episodeId);
        EpisodeLocalization localization = episodeLocalizationRepository.findByEpisodeIdAndLocale(episodeId, normalizeLocale(locale))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Episode localization not found"));
        localization.update(request.title().trim(), trimToNull(request.description()));
        return getEpisode(episodeId);
    }

    @Transactional
    public void deleteEpisodeLocalization(Long episodeId, String locale) {
        requireEpisode(episodeId);
        episodeLocalizationRepository.deleteByEpisodeIdAndLocale(episodeId, normalizeLocale(locale));
    }

    @Transactional
    public MediaVersionResponse createContentMediaVersion(Long contentId, MediaVersionRequest request) {
        Content content = requireContent(contentId);
        if (content.getType() != ContentType.MOVIE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content media version is allowed only for MOVIE");
        }
        if (mediaVersionRepository.existsByContentIdAndVersionType(contentId, request.versionType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Media version type already exists");
        }
        return mediaVersionResponse(mediaVersionRepository.save(new MediaVersion(
                contentId,
                null,
                request.versionType(),
                request.status()
        )));
    }

    @Transactional(readOnly = true)
    public List<MediaVersionResponse> listContentMediaVersions(Long contentId) {
        Content content = requireContent(contentId);
        if (content.getType() != ContentType.MOVIE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content media versions are allowed only for MOVIE");
        }
        return mediaVersionResponses(mediaVersionRepository.findByContentIdOrderByIdAsc(contentId));
    }

    @Transactional
    public MediaVersionResponse createEpisodeMediaVersion(Long episodeId, MediaVersionRequest request) {
        requireEpisode(episodeId);
        if (mediaVersionRepository.existsByEpisodeIdAndVersionType(episodeId, request.versionType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Media version type already exists");
        }
        return mediaVersionResponse(mediaVersionRepository.save(new MediaVersion(
                null,
                episodeId,
                request.versionType(),
                request.status()
        )));
    }

    @Transactional(readOnly = true)
    public List<MediaVersionResponse> listEpisodeMediaVersions(Long episodeId) {
        requireEpisode(episodeId);
        return mediaVersionResponses(mediaVersionRepository.findByEpisodeIdOrderByIdAsc(episodeId));
    }

    @Transactional(readOnly = true)
    public MediaVersionResponse getMediaVersion(Long mediaVersionId) {
        return mediaVersionResponse(requireMediaVersion(mediaVersionId));
    }

    @Transactional
    public MediaVersionResponse updateMediaVersion(Long mediaVersionId, MediaVersionRequest request) {
        MediaVersion mediaVersion = requireMediaVersion(mediaVersionId);
        validateMediaVersionTypeUniqueness(mediaVersion, request.versionType());
        mediaVersion.update(request.versionType(), request.status());
        return mediaVersionResponse(mediaVersion);
    }

    @Transactional
    public MediaVersionResponse attachVideo(Long mediaVersionId, AttachVideoRequest request) {
        MediaVersion mediaVersion = requireMediaVersion(mediaVersionId);
        Video video = videoRepository.findById(request.videoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));
        if (mediaVersion.getVideoId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Media version already has a video");
        }
        mediaVersionRepository.findByVideoId(video.getId()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Video is already attached to another media version");
        });
        mediaVersion.attachVideo(video.getId());
        return mediaVersionResponse(mediaVersion);
    }

    private void validateMediaVersionTypeUniqueness(MediaVersion mediaVersion, MediaVersionType nextType) {
        if (mediaVersion.getVersionType() == nextType) {
            return;
        }
        if (mediaVersion.getContentId() != null
                && mediaVersionRepository.existsByContentIdAndVersionType(mediaVersion.getContentId(), nextType)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Media version type already exists");
        }
        if (mediaVersion.getEpisodeId() != null
                && mediaVersionRepository.existsByEpisodeIdAndVersionType(mediaVersion.getEpisodeId(), nextType)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Media version type already exists");
        }
    }

    private Content requireContent(Long contentId) {
        return contentRepository.findById(contentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found"));
    }

    private void requireSeries(Content content) {
        if (content.getType() != ContentType.SERIES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Season is allowed only for SERIES content");
        }
    }

    private Season requireSeason(Long seasonId) {
        return seasonRepository.findById(seasonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Season not found"));
    }

    private Episode requireEpisode(Long episodeId) {
        return episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Episode not found"));
    }

    private MediaVersion requireMediaVersion(Long mediaVersionId) {
        return mediaVersionRepository.findById(mediaVersionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media version not found"));
    }

    private SeasonResponse seasonResponse(Season season) {
        return new SeasonResponse(
                season.getId(),
                season.getSeriesContentId(),
                season.getSeasonNumber(),
                season.getStatus(),
                season.getCreatedAt(),
                season.getUpdatedAt()
        );
    }

    private EpisodeResponse episodeResponse(Episode episode) {
        return episodeResponse(
                episode,
                episodeLocalizationRepository.findByEpisodeIdOrderByLocaleAsc(episode.getId()).stream()
                        .map(this::episodeLocalizationResponse)
                        .toList()
        );
    }

    private EpisodeResponse episodeResponse(Episode episode, List<EpisodeLocalizationResponse> localizations) {
        return new EpisodeResponse(
                episode.getId(),
                episode.getSeasonId(),
                episode.getEpisodeNumber(),
                episode.getStatus(),
                episode.getReleaseAt(),
                episode.getCreatedAt(),
                episode.getUpdatedAt(),
                localizations
        );
    }

    private Map<Long, List<EpisodeLocalizationResponse>> localizationsByEpisodeId(List<Long> episodeIds) {
        if (episodeIds.isEmpty()) {
            return Map.of();
        }
        return episodeLocalizationRepository.findByEpisodeIdInOrderByEpisodeIdAscLocaleAsc(episodeIds).stream()
                .collect(Collectors.groupingBy(
                        EpisodeLocalization::getEpisodeId,
                        Collectors.mapping(this::episodeLocalizationResponse, Collectors.toList())
                ));
    }

    private EpisodeLocalizationResponse episodeLocalizationResponse(EpisodeLocalization localization) {
        return new EpisodeLocalizationResponse(
                localization.getId(),
                localization.getLocale(),
                localization.getTitle(),
                localization.getDescription(),
                localization.getCreatedAt(),
                localization.getUpdatedAt()
        );
    }

    private MediaVersionResponse mediaVersionResponse(MediaVersion mediaVersion) {
        VideoSummary video = mediaVersion.getVideoId() == null
                ? null
                : videoRepository.findById(mediaVersion.getVideoId()).map(this::videoSummary).orElse(null);
        return new MediaVersionResponse(
                mediaVersion.getId(),
                mediaVersion.getContentId(),
                mediaVersion.getEpisodeId(),
                mediaVersion.getVideoId(),
                mediaVersion.getVersionType(),
                mediaVersion.getStatus(),
                mediaVersion.getCreatedAt(),
                mediaVersion.getUpdatedAt(),
                video
        );
    }

    private List<MediaVersionResponse> mediaVersionResponses(List<MediaVersion> mediaVersions) {
        Map<Long, VideoSummary> videosById = videoSummariesById(mediaVersions.stream()
                .map(MediaVersion::getVideoId)
                .filter(Objects::nonNull)
                .toList());
        return mediaVersions.stream()
                .map(mediaVersion -> new MediaVersionResponse(
                        mediaVersion.getId(),
                        mediaVersion.getContentId(),
                        mediaVersion.getEpisodeId(),
                        mediaVersion.getVideoId(),
                        mediaVersion.getVersionType(),
                        mediaVersion.getStatus(),
                        mediaVersion.getCreatedAt(),
                        mediaVersion.getUpdatedAt(),
                        mediaVersion.getVideoId() == null ? null : videosById.get(mediaVersion.getVideoId())
                ))
                .toList();
    }

    private Map<Long, VideoSummary> videoSummariesById(List<Long> videoIds) {
        if (videoIds.isEmpty()) {
            return Map.of();
        }
        return videoRepository.findAllById(videoIds).stream()
                .map(this::videoSummary)
                .collect(Collectors.toMap(VideoSummary::id, video -> video));
    }

    private VideoSummary videoSummary(Video video) {
        return new VideoSummary(
                video.getId(),
                video.getTitle(),
                video.getStatus(),
                video.getActiveVideoFileId(),
                video.getPublishedMediaPackageId()
        );
    }

    private void validatePositive(int value, String fieldName) {
        if (value < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be greater than or equal to 1");
        }
    }

    private String normalizeLocale(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Locale is required");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
