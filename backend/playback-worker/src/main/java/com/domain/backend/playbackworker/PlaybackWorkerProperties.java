package com.domain.backend.playbackworker;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "playback.worker")
public class PlaybackWorkerProperties {

    private String watchEventsTopic = "watch-events";
    private String watchEventsDlqTopic = "watch-events.dlq";
    private String consumerGroupId = "playback-progress-group";
    private Duration progressTtl = Duration.ofDays(30);
    private Duration progressCheckpointInterval = Duration.ofSeconds(60);
    private Duration flushInterval = Duration.ofSeconds(10);
    private Duration flushClaimTtl = Duration.ofSeconds(30);
    private int flushBatchSize = 500;

    public String getWatchEventsTopic() {
        return watchEventsTopic;
    }

    public void setWatchEventsTopic(String watchEventsTopic) {
        this.watchEventsTopic = watchEventsTopic;
    }

    public String getWatchEventsDlqTopic() {
        return watchEventsDlqTopic;
    }

    public void setWatchEventsDlqTopic(String watchEventsDlqTopic) {
        this.watchEventsDlqTopic = watchEventsDlqTopic;
    }

    public String getConsumerGroupId() {
        return consumerGroupId;
    }

    public void setConsumerGroupId(String consumerGroupId) {
        this.consumerGroupId = consumerGroupId;
    }

    public Duration getProgressTtl() {
        return progressTtl;
    }

    public void setProgressTtl(Duration progressTtl) {
        this.progressTtl = progressTtl;
    }

    public Duration getProgressCheckpointInterval() {
        return progressCheckpointInterval;
    }

    public void setProgressCheckpointInterval(Duration progressCheckpointInterval) {
        this.progressCheckpointInterval = progressCheckpointInterval;
    }

    public Duration getFlushInterval() {
        return flushInterval;
    }

    public void setFlushInterval(Duration flushInterval) {
        this.flushInterval = flushInterval;
    }

    public Duration getFlushClaimTtl() {
        return flushClaimTtl;
    }

    public void setFlushClaimTtl(Duration flushClaimTtl) {
        this.flushClaimTtl = flushClaimTtl;
    }

    public int getFlushBatchSize() {
        return flushBatchSize;
    }

    public void setFlushBatchSize(int flushBatchSize) {
        this.flushBatchSize = flushBatchSize;
    }
}
