package com.domain.backend.subscription;

import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SubscriptionKafkaConfig {
    @Bean
    DefaultKafkaProducerFactory<String, String> subscriptionProducerFactory(@Value("${spring.kafka.bootstrap-servers}") String servers) {
        return new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all", ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 200, ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 3000));
    }
    @Bean
    KafkaTemplate<String, String> subscriptionKafkaTemplate(DefaultKafkaProducerFactory<String, String> subscriptionProducerFactory) {
        return new KafkaTemplate<>(subscriptionProducerFactory);
    }
    @Bean
    NewTopic analyticsSubscriptionTopic(@Value("${app.analytics.subscription-topic:subscription-events}") String topic,
                                       @Value("${app.analytics.partitions:12}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }
}
