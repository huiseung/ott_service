package com.domain.backend.subscription;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.subscription.outbox-enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionOutboxRelay {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionOutboxRelay.class);
    private final SubscriptionOutboxStore store;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    public SubscriptionOutboxRelay(SubscriptionOutboxStore store,
            @Qualifier("subscriptionKafkaTemplate") KafkaTemplate<String, String> kafka,
            @Value("${app.analytics.subscription-topic:subscription-events}") String topic) {
        this.store = store; this.kafka = kafka; this.topic = topic;
    }
    @Scheduled(fixedDelayString = "${app.subscription.outbox-poll-ms:1000}")
    @Transactional(propagation = Propagation.NEVER)
    public void publish() {
        for (var row : store.claim()) {
            try {
                kafka.send(topic, row.aggregateId(), row.payload()).get(4, TimeUnit.SECONDS);
                store.delivered(row);
            } catch (Exception failure) {
                store.failed(row, failure.getClass().getSimpleName());
                LOG.warn("Subscription outbox delivery deferred: row={}, attempt={}", row.id(), row.attempts());
                if (failure instanceof InterruptedException) { Thread.currentThread().interrupt(); return; }
            }
        }
    }
}
