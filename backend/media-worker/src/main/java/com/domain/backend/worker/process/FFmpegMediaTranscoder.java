package com.domain.backend.worker.process;

import com.domain.backend.media.domain.MediaProcessingStage;
import com.domain.backend.worker.config.MediaProfileProperties;
import com.domain.backend.worker.config.MediaWorkerProperties;
import com.domain.backend.worker.job.ClaimedMediaJob;
import com.domain.backend.worker.job.MediaJobStateService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class FFmpegMediaTranscoder implements MediaTranscoder {

    private final MediaProfileProperties profile;
    private final MediaWorkerProperties workerProperties;
    private final MediaJobStateService stateService;

    public FFmpegMediaTranscoder(MediaProfileProperties profile, MediaWorkerProperties workerProperties,
                                 MediaJobStateService stateService) {
        this.profile = profile;
        this.workerProperties = workerProperties;
        this.stateService = stateService;
    }

    @Override
    public void transcode(ClaimedMediaJob job, ScratchWorkspace workspace, MediaProbeResult probeResult) {
        try {
            Path renditionDir = workspace.outputDirectory().resolve(profile.getRenditionName());
            Files.createDirectories(renditionDir);
            writeMasterManifest(workspace.outputDirectory(), probeResult);
            stateService.updateStage(job, MediaProcessingStage.TRANSCODING, 0);

            List<String> command = ffmpegCommand(workspace.sourceFile(), renditionDir);
            Process process = new ProcessBuilder(command).start();
            AtomicReference<String> stderrTail = new AtomicReference<>("");
            var executor = Executors.newFixedThreadPool(2);
            executor.submit(() -> consumeStderr(process, stderrTail));
            executor.submit(() -> consumeProgress(job, process, probeResult.durationMs()));

            boolean finished = process.waitFor(workerProperties.getFfmpegTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new MediaProcessingException("FFMPEG_PROCESS_TIMEOUT", "FFmpeg timed out", true);
            }
            executor.shutdownNow();
            if (process.exitValue() != 0) {
                throw new MediaProcessingException("FFMPEG_PROCESS_CRASH", stderrTail.get(), true);
            }
            stateService.updateProgress(job, probeResult.durationMs(), probeResult.durationMs(), 100);
        } catch (IOException e) {
            throw new MediaProcessingException("FFMPEG_PROCESS_CRASH", e.getMessage(), true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaProcessingException("WORKER_INTERRUPTED", e.getMessage(), true);
        }
    }

    private List<String> ffmpegCommand(Path source, Path renditionDir) {
        List<String> command = new ArrayList<>();
        command.add(profile.getFfmpegBinary());
        command.addAll(List.of(
                "-y",
                "-i", source.toString(),
                "-map", "0:v:0",
                "-map", "0:a:0",
                "-c:v", profile.getVideoCodec(),
                "-preset", profile.getVideoPreset(),
                "-crf", String.valueOf(profile.getVideoCrf()),
                "-vf", "scale='min(%d,iw)':'min(%d,ih)':force_original_aspect_ratio=decrease".formatted(profile.getMaxWidth(), profile.getMaxHeight()),
                "-c:a", profile.getAudioCodec(),
                "-b:a", profile.getAudioBitrate(),
                "-hls_time", String.valueOf(profile.getSegmentDuration()),
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", renditionDir.resolve("segment%06d.ts").toString(),
                "-force_key_frames", "expr:gte(t,n_forced*" + profile.getSegmentDuration() + ")",
                "-progress", "pipe:1",
                "-nostats",
                renditionDir.resolve("index.m3u8").toString()
        ));
        return command;
    }

    private void writeMasterManifest(Path outputDirectory, MediaProbeResult probeResult) throws IOException {
        int width = Math.min(profile.getMaxWidth(), probeResult.width());
        int height = Math.min(profile.getMaxHeight(), probeResult.height());
        String content = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=%dx%d,CODECS="avc1.64001f,mp4a.40.2"
                %s/index.m3u8
                """.formatted(width, height, profile.getRenditionName());
        Files.writeString(outputDirectory.resolve("master.m3u8"), content, StandardCharsets.UTF_8);
    }

    private void consumeProgress(ClaimedMediaJob job, Process process, long durationMs) {
        Instant lastUpdate = Instant.EPOCH;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("out_time")) {
                    continue;
                }
                long processedMs = parseProcessedMs(line);
                int percent = durationMs <= 0 ? 0 : (int) Math.min(99, (processedMs * 100) / durationMs);
                Instant now = Instant.now();
                if (now.isAfter(lastUpdate.plus(workerProperties.getProgressUpdateInterval()))) {
                    if (!stateService.updateProgress(job, processedMs, durationMs, percent)) {
                        process.destroyForcibly();
                        return;
                    }
                    lastUpdate = now;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void consumeStderr(Process process, AtomicReference<String> tail) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            StringBuilder buffer = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                buffer.append(line).append('\n');
                if (buffer.length() > 4000) {
                    buffer.delete(0, buffer.length() - 4000);
                }
                tail.set(buffer.toString());
            }
        } catch (IOException ignored) {
        }
    }

    private long parseProcessedMs(String line) {
        String value = line.substring(line.indexOf('=') + 1).trim();
        if (line.startsWith("out_time_us")) {
            return Long.parseLong(value) / 1000;
        }
        if (line.startsWith("out_time_ms")) {
            return Long.parseLong(value) / 1000;
        }
        String[] hms = value.split(":");
        if (hms.length == 3) {
            double seconds = Integer.parseInt(hms[0]) * 3600
                    + Integer.parseInt(hms[1]) * 60
                    + Double.parseDouble(hms[2]);
            return (long) (seconds * 1000);
        }
        return 0;
    }
}
