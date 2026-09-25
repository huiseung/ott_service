package com.domain.backend.analytics;

import static org.assertj.core.api.Assertions.*;

import com.domain.backend.playback.domain.WatchEventType;
import com.domain.backend.playback.event.AnalyticsEvent;
import com.domain.backend.playback.event.WatchEvent;
import com.domain.backend.user.application.PlaybackProperties;
import com.domain.backend.user.config.PlaybackKafkaConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(AnalyticsKafkaIntegrationTest.Config.class)
@EmbeddedKafka(partitions = 2, topics = {"playback-events", "behavior-events", "watch-events"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class AnalyticsKafkaIntegrationTest {
    @Configuration
    @Import({AnalyticsKafkaConfig.class, PlaybackKafkaConfig.class, AnalyticsPublisher.class})
    static class Config {
        @Bean PlaybackProperties playbackProperties() { return new PlaybackProperties(); }
    }

    @Autowired EmbeddedKafkaBroker broker;
    @Autowired AnalyticsPublisher publisher;

    @Test
    void publishesIndependentJsonRecordsAndPreservesExistingProgressContract() throws Exception {
        var props = KafkaTestUtils.consumerProps("analytics-test", "false", broker);
        try (var consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromEmbeddedTopics(consumer, "playback-events", "behavior-events", "watch-events");
            var id = UUID.randomUUID();
            var now = Instant.now();
            var raw = new AnalyticsEvent(id, "HEARTBEAT", 1, now, now, 7L, "anon", "visit", "user-web", "WEB",
                    "playback", 2L, 30L, 10L, Map.of("positionMs", 1234), null);
            var progress = new WatchEvent(id, WatchEventType.PROGRESS, "playback", 5L, 7L, 10L, 20L, 1, 60, 2, now, now);
            var behavior = new AnalyticsEvent(UUID.randomUUID(), "CONTENT_CLICK", 1, now, now, null, "anon", "visit",
                    "user-web", "WEB", null, null, 30L, 10L, Map.of("surface", "home"), null);
            publisher.publish(List.of(new AnalyticsCollector.Collected(raw, progress),
                    new AnalyticsCollector.Collected(behavior, null))).get(10, TimeUnit.SECONDS);
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (records.size() < 3 && System.nanoTime() < deadline)
                consumer.poll(Duration.ofMillis(250)).forEach(records::add);
            assertThat(records).hasSize(3);
            var playback = records.stream().filter(r -> r.topic().equals("playback-events")).findFirst().orElseThrow();
            assertThat(playback.key()).isEqualTo("playback");
            assertThat(playback.value()).contains(id.toString(), "\"positionMs\":1234", "\"eventVersion\":1");
            var legacy = records.stream().filter(r -> r.topic().equals("watch-events")).findFirst().orElseThrow();
            assertThat(legacy.value()).contains(id.toString(), "\"positionSeconds\":1", "\"eventType\":\"PROGRESS\"");
            try (var decoder = new org.springframework.kafka.support.serializer.JacksonJsonDeserializer<>(WatchEvent.class, false)) {
                var decoded = decoder.deserialize("watch-events", legacy.value().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                assertThat(decoded.eventId()).isEqualTo(id);
                assertThat(decoded.occurredAt()).isEqualTo(now);
            }
            assertThat(records.stream().filter(r -> r.topic().equals("behavior-events")).findFirst().orElseThrow().key())
                    .isEqualTo("visit");
        }
    }
}
