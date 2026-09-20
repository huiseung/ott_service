package com.domain.backend.worker.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media.profile")
public class MediaProfileProperties {

    private String version = "hls-720p-v1";
    private String renditionName = "720p";
    private int maxWidth = 1280;
    private int maxHeight = 720;
    private int segmentDuration = 4;
    private String videoCodec = "libx264";
    private String audioCodec = "aac";
    private int videoCrf = 21;
    private String videoPreset = "veryfast";
    private String audioBitrate = "128k";
    private int maxSourceWidth = 1920;
    private int maxSourceHeight = 1080;
    private double maxSourceFps = 60.0;
    private Duration maxDuration = Duration.ofMinutes(65);
    private String ffmpegBinary = "ffmpeg";
    private String ffprobeBinary = "ffprobe";

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getRenditionName() { return renditionName; }
    public void setRenditionName(String renditionName) { this.renditionName = renditionName; }
    public int getMaxWidth() { return maxWidth; }
    public void setMaxWidth(int maxWidth) { this.maxWidth = maxWidth; }
    public int getMaxHeight() { return maxHeight; }
    public void setMaxHeight(int maxHeight) { this.maxHeight = maxHeight; }
    public int getSegmentDuration() { return segmentDuration; }
    public void setSegmentDuration(int segmentDuration) { this.segmentDuration = segmentDuration; }
    public String getVideoCodec() { return videoCodec; }
    public void setVideoCodec(String videoCodec) { this.videoCodec = videoCodec; }
    public String getAudioCodec() { return audioCodec; }
    public void setAudioCodec(String audioCodec) { this.audioCodec = audioCodec; }
    public int getVideoCrf() { return videoCrf; }
    public void setVideoCrf(int videoCrf) { this.videoCrf = videoCrf; }
    public String getVideoPreset() { return videoPreset; }
    public void setVideoPreset(String videoPreset) { this.videoPreset = videoPreset; }
    public String getAudioBitrate() { return audioBitrate; }
    public void setAudioBitrate(String audioBitrate) { this.audioBitrate = audioBitrate; }
    public int getMaxSourceWidth() { return maxSourceWidth; }
    public void setMaxSourceWidth(int maxSourceWidth) { this.maxSourceWidth = maxSourceWidth; }
    public int getMaxSourceHeight() { return maxSourceHeight; }
    public void setMaxSourceHeight(int maxSourceHeight) { this.maxSourceHeight = maxSourceHeight; }
    public double getMaxSourceFps() { return maxSourceFps; }
    public void setMaxSourceFps(double maxSourceFps) { this.maxSourceFps = maxSourceFps; }
    public Duration getMaxDuration() { return maxDuration; }
    public void setMaxDuration(Duration maxDuration) { this.maxDuration = maxDuration; }
    public String getFfmpegBinary() { return ffmpegBinary; }
    public void setFfmpegBinary(String ffmpegBinary) { this.ffmpegBinary = ffmpegBinary; }
    public String getFfprobeBinary() { return ffprobeBinary; }
    public void setFfprobeBinary(String ffprobeBinary) { this.ffprobeBinary = ffprobeBinary; }
}
