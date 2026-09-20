package com.domain.backend.worker.process;

public record ProcessOutput(int exitCode, String stdout, String stderr) {
}
