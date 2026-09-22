package com.domain.backend.user.application;

import com.domain.backend.playback.event.WatchEvent;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class WatchEventPublisher {

    private final KafkaTemplate<String, WatchEvent> kafkaTemplate;
    private final PlaybackProperties properties;

    public WatchEventPublisher(KafkaTemplate<String, WatchEvent> kafkaTemplate, PlaybackProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public UUID publish(WatchEvent event) {
        try {
            kafkaTemplate.send(properties.getWatchEventsTopic(), event.playbackSessionId(), event)
                    .get(properties.getPublishTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return event.eventId();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Watch event pipeline is unavailable", e);
        }
    }
}
