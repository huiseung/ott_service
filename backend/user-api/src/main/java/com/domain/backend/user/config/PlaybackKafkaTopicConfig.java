package com.domain.backend.user.config;

import com.domain.backend.user.application.PlaybackProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class PlaybackKafkaTopicConfig {

    @Bean
    NewTopic watchEventsTopic(
            PlaybackProperties properties,
            @Value("${app.playback.watch-events-partitions:12}") int partitions
    ) {
        return TopicBuilder.name(properties.getWatchEventsTopic())
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic watchEventsDlqTopic(@Value("${app.playback.watch-events-dlq-topic:watch-events.dlq}") String topic) {
        return TopicBuilder.name(topic)
                .partitions(12)
                .replicas(1)
                .build();
    }
}
