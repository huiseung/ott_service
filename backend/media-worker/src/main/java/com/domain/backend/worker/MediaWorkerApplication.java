package com.domain.backend.worker;

import com.domain.backend.video.infrastructure.storage.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "com.domain.backend.worker",
        "com.domain.backend.video.infrastructure.storage"
})
@EntityScan(basePackages = {
        "com.domain.backend.media.domain",
        "com.domain.backend.video.domain"
})
@EnableJpaRepositories(basePackages = {
        "com.domain.backend.media.infrastructure.persistence",
        "com.domain.backend.video.infrastructure.persistence"
})
@EnableConfigurationProperties(StorageProperties.class)
public class MediaWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaWorkerApplication.class, args);
    }
}
