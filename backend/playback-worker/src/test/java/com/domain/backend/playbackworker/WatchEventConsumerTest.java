package com.domain.backend.playbackworker;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.domain.backend.playback.domain.WatchEventType;
import com.domain.backend.playback.event.WatchEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class WatchEventConsumerTest {

    @Test
    void acknowledgesOnlyAfterRedisUpdateSucceeds() {
        var store = mock(RedisProgressStore.class);
        var consumer = new WatchEventConsumer(store, new SimpleMeterRegistry());
        var ack = mock(Acknowledgment.class);
        WatchEvent event = event(1);
        when(store.apply(event)).thenReturn(true);

        consumer.consume(event, ack);

        verify(ack).acknowledge();
    }

    @Test
    void doesNotAcknowledgeWhenRedisUpdateFails() {
        var store = mock(RedisProgressStore.class);
        var consumer = new WatchEventConsumer(store, new SimpleMeterRegistry());
        var ack = mock(Acknowledgment.class);
        WatchEvent event = event(1);
        when(store.apply(event)).thenThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> consumer.consume(event, ack)).isInstanceOf(IllegalStateException.class);
        verify(ack, never()).acknowledge();
    }

    private WatchEvent event(long sequence) {
        return new WatchEvent(UUID.randomUUID(), WatchEventType.PROGRESS, "session-1", 55L, 7L, 100L, 200L,
                120, 3600, sequence, Instant.now(), Instant.now());
    }
}
