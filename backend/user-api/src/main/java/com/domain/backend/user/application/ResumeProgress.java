package com.domain.backend.user.application;

public record ResumeProgress(long positionSeconds, long durationSeconds) {

    public static ResumeProgress empty(long durationSeconds) {
        return new ResumeProgress(0, durationSeconds);
    }
}
