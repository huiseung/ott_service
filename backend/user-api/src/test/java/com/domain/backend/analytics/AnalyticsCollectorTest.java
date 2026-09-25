package com.domain.backend.analytics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.domain.backend.playback.domain.WatchEventType;
import com.domain.backend.user.application.UserPrincipal;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AnalyticsCollectorTest {
    final AnalyticsPlaybackLookup lookup = mock(AnalyticsPlaybackLookup.class);
    final AnalyticsPublisher publisher = mock(AnalyticsPublisher.class);
    final AnalyticsCollector collector = new AnalyticsCollector(lookup, publisher);
    final UserPrincipal user = new UserPrincipal(7L, "viewer", "Viewer");
    final Map<String, Object> facts = Map.of("positionMs", 1234, "previousPositionMs", 500,
            "playedMsSincePreviousEvent", 734, "durationMs", 60_000, "playbackRate", 1);

    @Test
    void preservesRawFactsAndIdsAndProjectsProgressWithServerIdentity() {
        when(lookup.find(any(), eq(7L), any())).thenReturn(Map.of("playback-1",
                new AnalyticsPlaybackLookup.Session("playback-1", 5L, 7L, 10L, 20L, 30L, 60_000, 101L, 1, 1, 102L)));
        when(publisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));
        var event = event(AnalyticsDtos.EventType.HEARTBEAT, facts);
        var result = collector.collect(new AnalyticsDtos.Batch(List.of(event)), user).join();
        var captured = captured();
        assertThat(result.acceptedEventIds()).containsExactly(event.eventId());
        assertThat(captured).hasSize(1);
        var raw = captured.getFirst().event();
        assertThat(raw.eventId()).isEqualTo(event.eventId());
        assertThat(raw.occurredAt()).isEqualTo(event.occurredAt());
        assertThat(raw.receivedAt()).isAfterOrEqualTo(event.occurredAt());
        assertThat(raw.payload()).isEqualTo(facts);
        assertThat(raw.contentId()).isEqualTo(30L);
        assertThat(raw.userId()).isEqualTo(7L);
        assertThat(raw.playbackContext().durationMs()).isEqualTo(60_000);
        assertThat(raw.playbackContext().episodeId()).isEqualTo(101L);
        assertThat(raw.playbackContext().nextEpisodeId()).isEqualTo(102L);
        var progress = captured.getFirst().progress();
        assertThat(progress.positionSeconds()).isEqualTo(1);
        assertThat(progress.eventId()).isEqualTo(event.eventId());
        assertThat(progress.eventType()).isEqualTo(WatchEventType.PROGRESS);
        verify(lookup).find(Set.of("playback-1"), 7L, raw.receivedAt());
    }

    @Test
    void anonymousBehaviorDoesNotQueryPlaybackOrCreateProgress() {
        when(publisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));
        var event = event(AnalyticsDtos.EventType.CONTENT_CLICK, Map.of("surface", "home"));
        collector.collect(new AnalyticsDtos.Batch(List.of(event)), null).join();
        assertThat(captured().getFirst().event().userId()).isNull();
        verifyNoInteractions(lookup);
    }

    @Test
    void rejectsAnonymousPlaybackBeforePublishingAnything() {
        assertThatThrownBy(() -> collector.collect(new AnalyticsDtos.Batch(List.of(
                event(AnalyticsDtos.EventType.HEARTBEAT, facts))), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verifyNoInteractions(publisher, lookup);
    }

    @Test
    void inaccessibleSessionRejectsWholeMixedBatch() {
        when(lookup.find(any(), anyLong(), any())).thenReturn(Map.of());
        assertThatThrownBy(() -> collector.collect(new AnalyticsDtos.Batch(List.of(
                event(AnalyticsDtos.EventType.CONTENT_CLICK, Map.of()), event(AnalyticsDtos.EventType.HEARTBEAT, facts))), user))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsMissingPlaybackFactsBeforeAnyExternalPublication() {
        assertThatThrownBy(() -> collector.collect(new AnalyticsDtos.Batch(List.of(
                event(AnalyticsDtos.EventType.HEARTBEAT, Map.of("positionMs", 100)))), user))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsDuplicateIdsWithinBatchButAllowsRetryAcrossRequests() {
        var event = event(AnalyticsDtos.EventType.CONTENT_CLICK, Map.of());
        assertThatThrownBy(() -> collector.collect(new AnalyticsDtos.Batch(List.of(event, event)), null))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(publisher);
        when(publisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));
        assertThat(collector.collect(new AnalyticsDtos.Batch(List.of(event)), null).join().acceptedEventIds())
                .isEqualTo(collector.collect(new AnalyticsDtos.Batch(List.of(event)), null).join().acceptedEventIds());
    }

    @Test
    void enforcesBatchAndEnvelopeValidation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new AnalyticsDtos.Batch(List.of()))).isNotEmpty();
            assertThat(validator.validate(new AnalyticsDtos.Batch(Collections.nCopies(51,
                    event(AnalyticsDtos.EventType.CONTENT_CLICK, Map.of()))))).isNotEmpty();
            assertThat(validator.validate(event(AnalyticsDtos.EventType.HEARTBEAT, facts))).isEmpty();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<AnalyticsCollector.Collected> captured() {
        ArgumentCaptor<List<AnalyticsCollector.Collected>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }

    private AnalyticsDtos.Event event(AnalyticsDtos.EventType type, Map<String, Object> payload) {
        return new AnalyticsDtos.Event(UUID.randomUUID(), type, 1, Instant.now().minusSeconds(1), "anonymous", "visit",
                "user-web", "WEB", type.playback() ? "playback-1" : null, type.playback() ? 1L : null,
                type.playback() ? null : 30L, type.playback() ? 10L : null, payload);
    }

    @Test void rejectsInvalidQoeBeforeLookupAndPublication() {
        for (Object duration : List.of(-1, 0.5, 86_400_001, "100")) {
            var payload = new HashMap<>(facts);
            payload.put("qoeVersion", 1);
            payload.put("bufferingDurationMs", duration);
            assertThatThrownBy(() -> collector.collect(new AnalyticsDtos.Batch(List.of(
                    event(AnalyticsDtos.EventType.BUFFER_ENDED, payload))), user)).isInstanceOf(ResponseStatusException.class);
        }
        var missingSource = new HashMap<>(facts);
        missingSource.putAll(Map.of("qoeVersion", 1, "fatal", true, "code", "networkError"));
        assertThatThrownBy(() -> collector.collect(new AnalyticsDtos.Batch(List.of(
                event(AnalyticsDtos.EventType.PLAYBACK_ERROR, missingSource))), user)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(lookup, publisher);
    }

    @Test void acceptsQoeWithoutProducingResumeProgressForBufferEvents() {
        when(lookup.find(any(), eq(7L), any())).thenReturn(Map.of("playback-1",
                new AnalyticsPlaybackLookup.Session("playback-1", 5L, 7L, 10L, 20L, 30L, 60_000, null, null, null, null)));
        when(publisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));
        var payload = new HashMap<>(facts);
        payload.putAll(Map.of("qoeVersion", 1, "bufferingDurationMs", 2000));
        collector.collect(new AnalyticsDtos.Batch(List.of(event(AnalyticsDtos.EventType.BUFFER_ENDED, payload))), user).join();
        assertThat(captured().getFirst().progress()).isNull();
    }
}
