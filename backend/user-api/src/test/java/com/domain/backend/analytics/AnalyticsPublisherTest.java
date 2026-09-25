package com.domain.backend.analytics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.domain.backend.playback.event.AnalyticsEvent;
import com.domain.backend.playback.event.WatchEvent;
import com.domain.backend.user.application.PlaybackProperties;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AnalyticsPublisherTest {
    @SuppressWarnings("unchecked")
    final KafkaTemplate<String, AnalyticsEvent> kafka = mock(KafkaTemplate.class);
    @SuppressWarnings("unchecked")
    final KafkaTemplate<String, WatchEvent> progress = mock(KafkaTemplate.class);

    @Test
    void sendsOneRecordPerEventWithDomainKeysAndWaitsForEveryAcknowledgement() {
        var executor = executor();
        try {
            var pending = new CompletableFuture<org.springframework.kafka.support.SendResult<String, AnalyticsEvent>>();
            when(kafka.send(anyString(), anyString(), any(AnalyticsEvent.class))).thenReturn(pending);
            var publisher = new AnalyticsPublisher(kafka, progress, executor, new PlaybackProperties(), "playback-events", "behavior-events");
            var playback = event("playback-1");
            var behavior = event(null);
            var result = publisher.publish(List.of(new AnalyticsCollector.Collected(playback, null),
                    new AnalyticsCollector.Collected(behavior, null)));
            verify(kafka, timeout(1000)).send("playback-events", "playback-1", playback);
            verify(kafka, timeout(1000)).send("behavior-events", "visit", behavior);
            assertThat(result).isNotDone();
            pending.complete(null);
            result.orTimeout(2, TimeUnit.SECONDS).join();
        } finally { executor.shutdown(); }
    }

    @Test
    void partialKafkaFailureDoesNotAcknowledgeBatch() {
        var executor = executor();
        try {
            when(kafka.send(anyString(), anyString(), any(AnalyticsEvent.class)))
                    .thenReturn(CompletableFuture.completedFuture(null))
                    .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
            var publisher = new AnalyticsPublisher(kafka, progress, executor, new PlaybackProperties(), "playback-events", "behavior-events");
            assertThatThrownBy(() -> publisher.publish(List.of(new AnalyticsCollector.Collected(event(null), null),
                            new AnalyticsCollector.Collected(event(null), null))).join())
                    .hasCauseInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        } finally { executor.shutdown(); }
    }

    private ThreadPoolTaskExecutor executor() {
        var executor = new AnalyticsKafkaConfig().analyticsExecutor();
        executor.initialize();
        return executor;
    }

    private AnalyticsEvent event(String playback) {
        return new AnalyticsEvent(UUID.randomUUID(), playback == null ? "CONTENT_CLICK" : "HEARTBEAT", 1,
                Instant.now(), Instant.now(), 7L, "anonymous", "visit", "user-web", "WEB", playback,
                playback == null ? null : 1L, 30L, 10L, Map.of("positionMs", 100), null);
    }
}
