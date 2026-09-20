package com.domain.backend.worker.process;

import java.nio.file.Path;

public record ScratchWorkspace(Path root, Path sourceFile, Path outputDirectory) {
}
