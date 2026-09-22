package com.domain.backend.playbackworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.domain.backend")
@EnableConfigurationProperties({PlaybackWorkerProperties.class})
@EnableScheduling
public class PlaybackWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlaybackWorkerApplication.class, args);
    }
}
