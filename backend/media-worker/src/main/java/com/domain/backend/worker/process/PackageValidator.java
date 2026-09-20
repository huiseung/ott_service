package com.domain.backend.worker.process;

import com.domain.backend.media.domain.MediaProcessingStage;
import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
import com.domain.backend.worker.config.MediaProfileProperties;
import com.domain.backend.worker.job.ClaimedMediaJob;
import com.domain.backend.worker.job.MediaJobStateService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PackageValidator {

    private final ObjectStorageClient storageClient;
    private final MediaProfileProperties profile;
    private final MediaJobStateService stateService;
    private final ExternalProcessRunner processRunner;

    public PackageValidator(ObjectStorageClient storageClient, MediaProfileProperties profile,
                            MediaJobStateService stateService, ExternalProcessRunner processRunner) {
        this.storageClient = storageClient;
        this.profile = profile;
        this.stateService = stateService;
        this.processRunner = processRunner;
    }

    public void validate(ClaimedMediaJob job, ScratchWorkspace workspace, PackageUploadResult uploadResult,
                         MediaProbeResult probeResult) {
        stateService.updateStage(job, MediaProcessingStage.VALIDATING_PACKAGE, 95);
        Path master = workspace.outputDirectory().resolve("master.m3u8");
        Path playlist = workspace.outputDirectory().resolve(profile.getRenditionName()).resolve("index.m3u8");
        if (!Files.exists(master) || !Files.exists(playlist)) {
            throw new MediaProcessingException("PACKAGE_VALIDATION_FAILED", "Missing HLS manifests", true);
        }
        var masterHead = storageClient.headObject(uploadResult.masterManifestKey());
        var playlistHead = storageClient.headObject(uploadResult.playlistKey());
        if (!masterHead.exists() || masterHead.contentLength() <= 0 || !playlistHead.exists() || playlistHead.contentLength() <= 0) {
            throw new MediaProcessingException("PACKAGE_VALIDATION_FAILED", "Uploaded manifests are missing", true);
        }
        List<String> segments = parseSegments(playlist);
        if (segments.isEmpty()) {
            throw new MediaProcessingException("PACKAGE_VALIDATION_FAILED", "HLS playlist has no segments", true);
        }
        for (String segment : segments) {
            String segmentKey = uploadResult.rootKey() + "/" + profile.getRenditionName() + "/" + segment;
            var metadata = storageClient.headObject(segmentKey);
            if (!metadata.exists() || metadata.contentLength() <= 0) {
                throw new MediaProcessingException("PACKAGE_VALIDATION_FAILED", "Missing segment: " + segment, true);
            }
        }
        validateSamples(workspace.outputDirectory().resolve(profile.getRenditionName()), segments);
        validateDurationCloseEnough(playlist, probeResult.durationMs());
    }

    private List<String> parseSegments(Path playlist) {
        try {
            return Files.readAllLines(playlist).stream()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .toList();
        } catch (IOException e) {
            throw new MediaProcessingException("PACKAGE_VALIDATION_FAILED", e.getMessage(), true);
        }
    }

    private void validateSamples(Path renditionDir, List<String> segments) {
        List<String> samples = new ArrayList<>();
        samples.add(segments.getFirst());
        samples.add(segments.get(segments.size() / 2));
        samples.add(segments.getLast());
        for (String sample : samples.stream().distinct().toList()) {
            ProcessOutput output = processRunner.run(List.of(
                    profile.getFfprobeBinary(),
                    "-v", "error",
                    "-select_streams", "v:0",
                    "-show_entries", "stream=codec_name",
                    "-of", "default=noprint_wrappers=1",
                    renditionDir.resolve(sample).toString()
            ), Duration.ofSeconds(30));
            if (output.exitCode() != 0) {
                throw new MediaProcessingException("PACKAGE_VALIDATION_FAILED", "Segment sample decode failed", true);
            }
        }
    }

    private void validateDurationCloseEnough(Path playlist, long sourceDurationMs) {
        try {
            double totalSeconds = Files.readAllLines(playlist).stream()
                    .filter(line -> line.startsWith("#EXTINF:"))
                    .map(line -> line.substring("#EXTINF:".length(), line.indexOf(',')))
                    .mapToDouble(Double::parseDouble)
                    .sum();
            long hlsMs = (long) (totalSeconds * 1000);
            long tolerance = Math.max(10_000, sourceDurationMs / 20);
            if (Math.abs(hlsMs - sourceDurationMs) > tolerance) {
                throw new MediaProcessingException("PACKAGE_VALIDATION_FAILED", "HLS duration differs from source", true);
            }
        } catch (IOException e) {
            throw new MediaProcessingException("PACKAGE_VALIDATION_FAILED", e.getMessage(), true);
        }
    }
}
