package com.domain.backend.worker.job;

import com.domain.backend.worker.config.MediaWorkerProperties;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WorkerIdentity {

    private final String workerId;

    public WorkerIdentity(MediaWorkerProperties properties) {
        if (StringUtils.hasText(properties.getWorkerId())) {
            this.workerId = properties.getWorkerId();
        } else {
            this.workerId = hostname() + "-" + ManagementFactory.getRuntimeMXBean().getName().split("@")[0] + "-" + UUID.randomUUID();
        }
    }

    public String value() {
        return workerId;
    }

    private String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "media-worker";
        }
    }
}
