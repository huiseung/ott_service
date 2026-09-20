package com.domain.backend.worker.process;

import com.domain.backend.worker.config.MediaWorkerProperties;
import com.domain.backend.worker.job.ClaimedMediaJob;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public class ScratchWorkspaceManager {

    private final MediaWorkerProperties properties;

    public ScratchWorkspaceManager(MediaWorkerProperties properties) {
        this.properties = properties;
    }

    public ScratchWorkspace prepare(ClaimedMediaJob job, long sourceSize) {
        try {
            Path root = Path.of(properties.getScratchDirectory(), String.valueOf(job.id()), String.valueOf(job.generation()));
            if (Files.exists(root) && !isInsideConfiguredRoot(root)) {
                throw new IllegalStateException("Scratch path escapes configured root");
            }
            long usable = Files.createDirectories(root).toFile().getUsableSpace();
            long required = sourceSize * 3 + properties.getMinimumFreeDisk().toBytes();
            if (usable < required) {
                throw new MediaProcessingException("INSUFFICIENT_SCRATCH_SPACE", "Not enough scratch disk space", true);
            }
            Path output = root.resolve("output");
            Files.createDirectories(output);
            return new ScratchWorkspace(root, root.resolve("source.mp4"), output);
        } catch (IOException e) {
            throw new MediaProcessingException("SCRATCH_PREPARE_FAILED", e.getMessage(), true);
        }
    }

    public void cleanup(ScratchWorkspace workspace, boolean keep) {
        if (keep || workspace == null || !Files.exists(workspace.root())) {
            return;
        }
        try {
            if (!isInsideConfiguredRoot(workspace.root())) {
                return;
            }
            try (var paths = Files.walk(workspace.root())) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        } catch (IOException ignored) {
        }
    }

    private boolean isInsideConfiguredRoot(Path path) {
        Path configured = Path.of(properties.getScratchDirectory()).toAbsolutePath().normalize();
        return path.toAbsolutePath().normalize().startsWith(configured);
    }
}
