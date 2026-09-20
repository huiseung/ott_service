package com.domain.backend.worker.process;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ExternalProcessRunner {

    public ProcessOutput run(List<String> command, Duration timeout) {
        try {
            Process process = new ProcessBuilder(command).start();
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new MediaProcessingException("PROCESS_TIMEOUT", "External process timed out", true);
            }
            return new ProcessOutput(process.exitValue(), stdout.join(), tail(stderr.join()));
        } catch (IOException e) {
            throw new MediaProcessingException("PROCESS_START_FAILED", e.getMessage(), true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaProcessingException("WORKER_INTERRUPTED", e.getMessage(), true);
        }
    }

    private String read(java.io.InputStream inputStream) {
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MediaProcessingException("PROCESS_OUTPUT_READ_FAILED", e.getMessage(), true);
        }
    }

    private String tail(String value) {
        if (value == null || value.length() <= 4000) {
            return value;
        }
        return value.substring(value.length() - 4000);
    }
}
