package com.domain.backend.video.application;

import com.domain.backend.video.application.VideoUploadDtos.UploadStatusResponse;
import com.domain.backend.video.domain.VideoFile;
import com.domain.backend.video.domain.VideoFileStatus;
import com.domain.backend.video.infrastructure.persistence.VideoFilePartRepository;
import com.domain.backend.video.infrastructure.persistence.VideoFileRepository;
import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
import java.util.HashSet;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VideoUploadQueryService {

    private final VideoFileRepository videoFileRepository;
    private final VideoFilePartRepository partRepository;
    private final ObjectStorageClient storageClient;

    public VideoUploadQueryService(VideoFileRepository videoFileRepository,
                                   VideoFilePartRepository partRepository,
                                   ObjectStorageClient storageClient) {
        this.videoFileRepository = videoFileRepository;
        this.partRepository = partRepository;
        this.storageClient = storageClient;
    }

    @Transactional(readOnly = true)
    public UploadStatusResponse getUploadStatus(Long videoFileId) {
        VideoFile videoFile = videoFileRepository.findById(videoFileId)
                .orElseThrow(() -> new UploadException(HttpStatus.NOT_FOUND, "VideoFile not found"));
        var confirmed = partRepository.findByIdVideoFileIdOrderByIdPartNumberAsc(videoFileId)
                .stream()
                .map(part -> part.getId().getPartNumber())
                .toList();
        var confirmedSet = new HashSet<>(confirmed);

        var storageParts = videoFile.getMultipartUploadId() == null || !canListMultipartParts(videoFile.getStatus())
                ? new HashSet<Integer>()
                : storageClient.listParts(videoFile.getObjectKey(), videoFile.getMultipartUploadId())
                        .stream()
                        .map(part -> part.partNumber())
                        .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        var retryParts = IntStream.rangeClosed(1, videoFile.getTotalParts())
                .filter(partNumber -> !confirmedSet.contains(partNumber))
                .boxed()
                .toList();

        storageParts.removeAll(confirmedSet);
        if (!storageParts.isEmpty()) {
            retryParts = IntStream.concat(retryParts.stream().mapToInt(Integer::intValue), storageParts.stream().mapToInt(Integer::intValue))
                    .distinct()
                    .sorted()
                    .boxed()
                    .toList();
        }

        return new UploadStatusResponse(
                videoFile.getId(),
                videoFile.getStatus(),
                videoFile.getPartSize(),
                videoFile.getTotalParts(),
                videoFile.getFingerprint(),
                confirmed,
                retryParts
        );
    }

    private boolean canListMultipartParts(VideoFileStatus status) {
        return status == VideoFileStatus.UPLOADING || status == VideoFileStatus.COMPLETING;
    }
}
