package com.domain.backend.user.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.playback")
public class PlaybackProperties {

    private String watchEventsTopic = "watch-events";
    private Duration publishTimeout = Duration.ofSeconds(2);
    private Duration resumeCacheTtl = Duration.ofHours(24);

    public String getWatchEventsTopic() {
        return watchEventsTopic;
    }

    public void setWatchEventsTopic(String watchEventsTopic) {
        this.watchEventsTopic = watchEventsTopic;
    }

    public Duration getPublishTimeout() {
        return publishTimeout;
    }

    public void setPublishTimeout(Duration publishTimeout) {
        this.publishTimeout = publishTimeout;
    }

    public Duration getResumeCacheTtl() {
        return resumeCacheTtl;
    }

    public void setResumeCacheTtl(Duration resumeCacheTtl) {
        this.resumeCacheTtl = resumeCacheTtl;
    }
}
