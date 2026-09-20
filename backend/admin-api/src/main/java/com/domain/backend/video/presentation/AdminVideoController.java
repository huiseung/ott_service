package com.domain.backend.video.presentation;

import com.domain.backend.video.application.VideoUploadDtos.AckPartsRequest;
import com.domain.backend.video.application.VideoUploadDtos.CreateVideoRequest;
import com.domain.backend.video.application.VideoUploadDtos.CreateVideoResponse;
import com.domain.backend.video.application.VideoUploadDtos.PresignPartsRequest;
import com.domain.backend.video.application.VideoUploadDtos.PresignPartsResponse;
import com.domain.backend.video.application.VideoUploadDtos.UploadStatusResponse;
import com.domain.backend.media.application.AdminMediaDtos.AdminVideoDetailResponse;
import com.domain.backend.media.application.AdminMediaDtos.AdminVideoListItem;
import com.domain.backend.media.application.AdminMediaDtos.MediaProcessingJobListItem;
import com.domain.backend.media.application.AdminMediaDtos.PageResponse;
import com.domain.backend.media.application.AdminMediaQueryService;
import com.domain.backend.media.application.MediaProcessingJobAdminService;
import com.domain.backend.media.domain.MediaProcessingJobStatus;
import com.domain.backend.video.application.VideoUploadQueryService;
import com.domain.backend.video.application.VideoUploadService;
import com.domain.backend.video.domain.VideoStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVideoController {

    private final VideoUploadService uploadService;
    private final VideoUploadQueryService queryService;
    private final AdminMediaQueryService adminMediaQueryService;
    private final MediaProcessingJobAdminService jobAdminService;

    public AdminVideoController(VideoUploadService uploadService, VideoUploadQueryService queryService,
                                AdminMediaQueryService adminMediaQueryService,
                                MediaProcessingJobAdminService jobAdminService) {
        this.uploadService = uploadService;
        this.queryService = queryService;
        this.adminMediaQueryService = adminMediaQueryService;
        this.jobAdminService = jobAdminService;
    }

    @PostMapping("/videos")
    public ResponseEntity<CreateVideoResponse> createVideo(
            @Valid @RequestBody CreateVideoRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(uploadService.createVideoAndUpload(request, idempotencyKey));
    }

    @GetMapping("/videos/{videoId}")
    public AdminVideoDetailResponse getVideo(@PathVariable Long videoId) {
        return adminMediaQueryService.getVideo(videoId);
    }

    @GetMapping("/videos")
    public PageResponse<AdminVideoListItem> listVideos(
            @RequestParam(required = false) VideoStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return adminMediaQueryService.listVideos(status, page, size);
    }

    @PostMapping("/video-files/{videoFileId}/parts/presign")
    public PresignPartsResponse presignParts(
            @PathVariable Long videoFileId,
            @Valid @RequestBody PresignPartsRequest request
    ) {
        return uploadService.presignParts(videoFileId, request);
    }

    @PostMapping("/video-files/{videoFileId}/parts")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acknowledgeParts(@PathVariable Long videoFileId, @Valid @RequestBody AckPartsRequest request) {
        uploadService.acknowledgeParts(videoFileId, request);
    }

    @GetMapping("/video-files/{videoFileId}/upload-status")
    public UploadStatusResponse uploadStatus(@PathVariable Long videoFileId) {
        return queryService.getUploadStatus(videoFileId);
    }

    @PostMapping("/video-files/{videoFileId}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@PathVariable Long videoFileId) {
        uploadService.complete(videoFileId);
    }

    @DeleteMapping("/video-files/{videoFileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abort(@PathVariable Long videoFileId) {
        uploadService.abort(videoFileId);
    }

    @PostMapping("/media-processing-jobs/{jobId}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retryJob(@PathVariable Long jobId) {
        jobAdminService.retryFailedJob(jobId);
    }

    @GetMapping("/media-processing-jobs")
    public PageResponse<MediaProcessingJobListItem> listProcessingJobs(
            @RequestParam(required = false) MediaProcessingJobStatus status,
            @RequestParam(required = false) Long videoId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return adminMediaQueryService.listProcessingJobs(status, videoId, page, size);
    }
}
