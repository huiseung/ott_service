package com.domain.backend.media.application;

import com.domain.backend.media.domain.MediaProcessingJob;
import com.domain.backend.media.infrastructure.persistence.MediaProcessingJobRepository;
import com.domain.backend.video.domain.Video;
import com.domain.backend.video.domain.VideoFile;
import com.domain.backend.video.infrastructure.persistence.VideoFileRepository;
import com.domain.backend.video.infrastructure.persistence.VideoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MediaProcessingEnqueueService {

    private final VideoRepository videoRepository;
    private final VideoFileRepository videoFileRepository;
    private final MediaProcessingJobRepository jobRepository;

    public MediaProcessingEnqueueService(VideoRepository videoRepository, VideoFileRepository videoFileRepository,
                                         MediaProcessingJobRepository jobRepository) {
        this.videoRepository = videoRepository;
        this.videoFileRepository = videoFileRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public void markVideoFileCompletedAndEnqueue(Long videoFileId) {
        VideoFile videoFile = videoFileRepository.findById(videoFileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "VideoFile not found"));
        videoFile.markCompleted();

        Video video = videoFile.getVideo();
        video.markProcessing(videoFile.getId());

        String profileVersion = MediaProcessingJob.DEFAULT_PROFILE_VERSION;
        String jobKey = MediaProcessingJob.jobKey(videoFile.getId(), profileVersion);
        if (jobRepository.findByJobKey(jobKey).isEmpty()) {
            jobRepository.save(new MediaProcessingJob(video.getId(), videoFile.getId(), profileVersion));
        }
    }
}
