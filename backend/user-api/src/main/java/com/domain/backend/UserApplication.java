package com.domain.backend;


import com.domain.backend.user.application.UserSecurityProperties;
import com.domain.backend.video.infrastructure.storage.S3StorageConfiguration;
import com.domain.backend.video.infrastructure.storage.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;


@EnableConfigurationProperties({UserSecurityProperties.class, StorageProperties.class})
@Import(S3StorageConfiguration.class)
@SpringBootApplication
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
