package com.domain.backend.worker.process;

import com.domain.backend.worker.config.MediaProfileProperties;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class MediaProbe {

    private final MediaProfileProperties profile;
    private final ExternalProcessRunner processRunner;

    public MediaProbe(MediaProfileProperties profile, ExternalProcessRunner processRunner) {
        this.profile = profile;
        this.processRunner = processRunner;
    }

    public MediaProbeResult probeAndValidate(Path source) {
        ProcessOutput output = processRunner.run(List.of(
                profile.getFfprobeBinary(),
                "-v", "error",
                "-print_format", "json",
                "-show_entries", "format=format_name,duration:stream=codec_type,codec_name,width,height,r_frame_rate",
                source.toString()
        ), profile.getMaxDuration().plusSeconds(30));
        if (output.exitCode() != 0) {
            throw new MediaProcessingException("INVALID_MEDIA", output.stderr(), false);
        }
        String json = output.stdout();
        if (!json.contains("\"format_name\"") || !json.toLowerCase().contains("mp4")) {
            throw new MediaProcessingException("INVALID_MEDIA", "Only MP4 container is supported", false);
        }

        String videoBlock = streamBlock(json, "video");
        String audioBlock = streamBlock(json, "audio");
        if (videoBlock == null || audioBlock == null) {
            throw new MediaProcessingException("INVALID_MEDIA", "Video and audio streams are required", false);
        }

        String videoCodec = stringField(videoBlock, "codec_name");
        String audioCodec = stringField(audioBlock, "codec_name");
        int width = intField(videoBlock, "width");
        int height = intField(videoBlock, "height");
        double fps = fps(stringField(videoBlock, "r_frame_rate"));
        long durationMs = (long) (doubleField(json, "duration") * 1000);

        if (!"h264".equals(videoCodec)) {
            throw new MediaProcessingException("UNSUPPORTED_VIDEO_CODEC", "Only H.264 source video is supported", false);
        }
        if (!"aac".equals(audioCodec)) {
            throw new MediaProcessingException("UNSUPPORTED_AUDIO_CODEC", "Only AAC source audio is supported", false);
        }
        if (width > profile.getMaxSourceWidth() || height > profile.getMaxSourceHeight()) {
            throw new MediaProcessingException("RESOLUTION_NOT_SUPPORTED", "Source resolution is too high", false);
        }
        if (fps > profile.getMaxSourceFps()) {
            throw new MediaProcessingException("FPS_NOT_SUPPORTED", "Source frame rate is too high", false);
        }
        if (durationMs > profile.getMaxDuration().toMillis()) {
            throw new MediaProcessingException("DURATION_NOT_SUPPORTED", "Source duration is too long", false);
        }

        return new MediaProbeResult(durationMs, videoCodec, audioCodec, width, height, fps);
    }

    private String streamBlock(String json, String type) {
        var matcher = Pattern.compile("\\{[^{}]*\"codec_type\"\\s*:\\s*\"" + type + "\"[^{}]*}", Pattern.DOTALL).matcher(json);
        return matcher.find() ? matcher.group() : null;
    }

    private String stringField(String json, String name) {
        var matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) {
            throw new MediaProcessingException("INVALID_MEDIA", "Missing ffprobe field: " + name, false);
        }
        return matcher.group(1);
    }

    private int intField(String json, String name) {
        var matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new MediaProcessingException("INVALID_MEDIA", "Missing ffprobe field: " + name, false);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private double doubleField(String json, String name) {
        var matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"?([0-9.]+)\"?").matcher(json);
        if (!matcher.find()) {
            throw new MediaProcessingException("INVALID_MEDIA", "Missing ffprobe field: " + name, false);
        }
        return Double.parseDouble(matcher.group(1));
    }

    private double fps(String value) {
        if (value == null || "0/0".equals(value)) {
            return 0;
        }
        String[] parts = value.split("/");
        if (parts.length == 2) {
            return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
        }
        return Double.parseDouble(value);
    }
}
