package com.domain.backend.analytics;

import com.domain.backend.playback.event.AnalyticsEvent;
import com.domain.backend.playback.event.WatchEvent;
import com.domain.backend.user.application.PlaybackProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AnalyticsPublisher {
    private final KafkaTemplate<String, AnalyticsEvent> analytics;
    private final KafkaTemplate<String, WatchEvent> progress;
    private final ThreadPoolTaskExecutor executor;
    private final PlaybackProperties playbackProperties;
    private final String playbackTopic;
    private final String behaviorTopic;

    public AnalyticsPublisher(@Qualifier("analyticsKafkaTemplate") KafkaTemplate<String, AnalyticsEvent> analytics,
                              @Qualifier("watchEventKafkaTemplate") KafkaTemplate<String, WatchEvent> progress,
                              @Qualifier("analyticsExecutor") ThreadPoolTaskExecutor executor,
                              PlaybackProperties playbackProperties,
                              @Value("${app.analytics.playback-topic:playback-events}") String playbackTopic,
                              @Value("${app.analytics.behavior-topic:behavior-events}") String behaviorTopic) {
        this.analytics = analytics;
        this.progress = progress;
        this.executor = executor;
        this.playbackProperties = playbackProperties;
        this.playbackTopic = playbackTopic;
        this.behaviorTopic = behaviorTopic;
    }

    public CompletableFuture<Void> publish(List<AnalyticsCollector.Collected> batch) {
        try {
            return CompletableFuture.runAsync(() -> {
                try {
                    List<CompletableFuture<?>> pending = new ArrayList<>();
                    for (var item : batch) {
                        var event = item.event();
                        boolean playback = event.playbackSessionId() != null;
                        pending.add(analytics.send(playback ? playbackTopic : behaviorTopic,
                                playback ? event.playbackSessionId() : event.sessionId(), event));
                        if (item.progress() != null) pending.add(progress.send(playbackProperties.getWatchEventsTopic(),
                                event.playbackSessionId(), item.progress()));
                    }
                    CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw unavailable(e);
                } catch (Exception e) {
                    throw unavailable(e);
                }
            }, executor);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(unavailable(e));
        }
    }

    private static ResponseStatusException unavailable(Exception cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Analytics pipeline is unavailable", cause);
    }
}
