package com.domain.backend.video.infrastructure.storage;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3StorageConfiguration {

    @Bean
    @ConditionalOnMissingBean
    S3Client s3Client(StorageProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(s3Configuration(properties));
        if (properties.getEndpoint() != null) {
            builder.endpointOverride(properties.getEndpoint());
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    S3Presigner s3Presigner(StorageProperties properties) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(s3Configuration(properties));
        if (properties.getPublicEndpoint() != null || properties.getEndpoint() != null) {
            builder.endpointOverride(properties.getPublicEndpoint() != null ? properties.getPublicEndpoint() : properties.getEndpoint());
        }
        return builder.build();
    }

    @Bean
    ObjectStorageClient objectStorageClient(S3Client s3Client, S3Presigner s3Presigner, StorageProperties properties) {
        return new S3ObjectStorageClient(s3Client, s3Presigner, properties, Instant::now);
    }

    private StaticCredentialsProvider credentials(StorageProperties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.getAccessKey(),
                properties.getSecretKey()
        ));
    }

    private S3Configuration s3Configuration(StorageProperties properties) {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(properties.isPathStyleAccess())
                .build();
    }
}
