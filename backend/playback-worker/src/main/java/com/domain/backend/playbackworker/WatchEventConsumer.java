package com.domain.backend.playbackworker;

import com.domain.backend.playback.event.WatchEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class WatchEventConsumer {

    private final RedisProgressStore progressStore;
    private final Counter successCounter;
    private final Counter staleCounter;
    private final Counter failureCounter;

    public WatchEventConsumer(RedisProgressStore progressStore, MeterRegistry meterRegistry) {
        this.progressStore = progressStore;
        this.successCounter = meterRegistry.counter("playback.progress.consumer.success");
        this.staleCounter = meterRegistry.counter("playback.progress.consumer.stale");
        this.failureCounter = meterRegistry.counter("playback.progress.consumer.failure");
    }

    @KafkaListener(
            topics = "${playback.worker.watch-events-topic:watch-events}",
            groupId = "${playback.worker.consumer-group-id:playback-progress-group}"
    )
    public void consume(WatchEvent event, Acknowledgment acknowledgment) {
        try {
            boolean accepted = progressStore.apply(event);
            if (accepted) {
                successCounter.increment();
            } else {
                staleCounter.increment();
            }
            acknowledgment.acknowledge();
        } catch (RuntimeException e) {
            failureCounter.increment();
            throw e;
        }
    }
}
