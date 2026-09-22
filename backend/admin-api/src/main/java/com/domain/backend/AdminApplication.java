package com.domain.backend;

import com.domain.backend.content.application.ImageAssetProperties;
import com.domain.backend.video.infrastructure.storage.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.domain.backend")
@EnableConfigurationProperties({StorageProperties.class, ImageAssetProperties.class})
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
