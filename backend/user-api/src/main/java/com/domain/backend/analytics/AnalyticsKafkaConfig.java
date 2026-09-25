package com.domain.backend.analytics;

import com.domain.backend.playback.event.AnalyticsEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AnalyticsKafkaConfig {
    @Bean
    DefaultKafkaProducerFactory<String, AnalyticsEvent> analyticsProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String servers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 200);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 3000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 8 * 1024 * 1024);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    KafkaTemplate<String, AnalyticsEvent> analyticsKafkaTemplate(
            DefaultKafkaProducerFactory<String, AnalyticsEvent> analyticsProducerFactory) {
        return new KafkaTemplate<>(analyticsProducerFactory);
    }

    @Bean
    ThreadPoolTaskExecutor analyticsExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("analytics-publish-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    @Bean
    NewTopic analyticsPlaybackTopic(@Value("${app.analytics.playback-topic:playback-events}") String topic,
                                   @Value("${app.analytics.partitions:12}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    @Bean
    NewTopic analyticsBehaviorTopic(@Value("${app.analytics.behavior-topic:behavior-events}") String topic,
                                   @Value("${app.analytics.partitions:12}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }
}
