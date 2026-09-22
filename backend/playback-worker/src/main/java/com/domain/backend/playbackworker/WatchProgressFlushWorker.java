package com.domain.backend.playbackworker;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WatchProgressFlushWorker {

    private final RedisProgressStore progressStore;
    private final WatchHistoryBatchWriter batchWriter;
    private final PlaybackWorkerProperties properties;
    private final Counter successCounter;
    private final Counter failureCounter;

    public WatchProgressFlushWorker(RedisProgressStore progressStore, WatchHistoryBatchWriter batchWriter,
                                    PlaybackWorkerProperties properties, MeterRegistry meterRegistry) {
        this.progressStore = progressStore;
        this.batchWriter = batchWriter;
        this.properties = properties;
        this.successCounter = meterRegistry.counter("playback.progress.flush.success");
        this.failureCounter = meterRegistry.counter("playback.progress.flush.failure");
    }

    @Scheduled(fixedDelayString = "${playback.worker.flush-interval:10s}")
    public void flush() {
        Instant now = Instant.now();
        progressStore.recoverExpiredClaims(now);
        List<String> members = progressStore.claimDue(properties.getFlushBatchSize(), now);
        if (members.isEmpty()) {
            return;
        }
        try {
            List<RedisProgressSnapshot> snapshots = members.stream()
                    .map(progressStore::getSnapshot)
                    .filter(Objects::nonNull)
                    .toList();
            batchWriter.upsert(snapshots);
            progressStore.completeFlush(members);
            successCounter.increment(snapshots.size());
        } catch (RuntimeException e) {
            failureCounter.increment(members.size());
            progressStore.requeue(members, Instant.now().plusSeconds(5));
            throw e;
        }
    }
}
