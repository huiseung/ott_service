package com.domain.backend.user.application;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {

    private final UserSecurityProperties properties;
    private final Map<String, ArrayDeque<Instant>> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(UserSecurityProperties properties) {
        this.properties = properties;
    }

    public boolean allow(String key) {
        Instant cutoff = Instant.now().minusSeconds(60);
        ArrayDeque<Instant> queue = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst().isBefore(cutoff)) {
                queue.removeFirst();
            }
            if (queue.size() >= properties.getLoginMaxAttemptsPerMinute()) {
                return false;
            }
            queue.addLast(Instant.now());
            return true;
        }
    }
}
