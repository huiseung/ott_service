package com.domain.backend.worker.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({MediaWorkerProperties.class, MediaProfileProperties.class})
public class WorkerConfiguration {
}
