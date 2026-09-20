package com.domain.backend.worker.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

@ConfigurationProperties(prefix = "media.worker")
public class MediaWorkerProperties {

    private boolean enabled = true;
    private String workerId;
    private Duration pollInterval = Duration.ofSeconds(5);
    private int maxConcurrentJobs = 1;
    private Duration heartbeatInterval = Duration.ofSeconds(10);
    private Duration leaseDuration = Duration.ofSeconds(60);
    private Duration progressUpdateInterval = Duration.ofSeconds(3);
    private int maxAttempts = 3;
    private String scratchDirectory = "./work/jobs";
    @DataSizeUnit(DataUnit.GIGABYTES)
    private DataSize minimumFreeDisk = DataSize.ofGigabytes(2);
    private Duration ffmpegTimeout = Duration.ofHours(2);
    private int packageUploadConcurrency = 4;
    private boolean retainFailedScratch = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
    public int getMaxConcurrentJobs() { return maxConcurrentJobs; }
    public void setMaxConcurrentJobs(int maxConcurrentJobs) { this.maxConcurrentJobs = maxConcurrentJobs; }
    public Duration getHeartbeatInterval() { return heartbeatInterval; }
    public void setHeartbeatInterval(Duration heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }
    public Duration getProgressUpdateInterval() { return progressUpdateInterval; }
    public void setProgressUpdateInterval(Duration progressUpdateInterval) { this.progressUpdateInterval = progressUpdateInterval; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public String getScratchDirectory() { return scratchDirectory; }
    public void setScratchDirectory(String scratchDirectory) { this.scratchDirectory = scratchDirectory; }
    public DataSize getMinimumFreeDisk() { return minimumFreeDisk; }
    public void setMinimumFreeDisk(DataSize minimumFreeDisk) { this.minimumFreeDisk = minimumFreeDisk; }
    public Duration getFfmpegTimeout() { return ffmpegTimeout; }
    public void setFfmpegTimeout(Duration ffmpegTimeout) { this.ffmpegTimeout = ffmpegTimeout; }
    public int getPackageUploadConcurrency() { return packageUploadConcurrency; }
    public void setPackageUploadConcurrency(int packageUploadConcurrency) { this.packageUploadConcurrency = packageUploadConcurrency; }
    public boolean isRetainFailedScratch() { return retainFailedScratch; }
    public void setRetainFailedScratch(boolean retainFailedScratch) { this.retainFailedScratch = retainFailedScratch; }
}
