package com.domain.backend.analytics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminAnalyticsServiceTest {
    final AnalyticsReader reader = mock(AnalyticsReader.class);
    final AdminAnalyticsService service = new AdminAnalyticsService(reader);
    final LocalDate day = LocalDate.of(2026, 9, 1);
    @Test void rejectsInvalidFiltersBeforeAnyAnalyticsIo() {
        assertThatThrownBy(() -> service.dashboard(0, day, day)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.dashboard(1, day, day.minusDays(1))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.dashboard(1, day, day.plusDays(31))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(reader);
    }
    @Test void accepts31DaysAndDoesNotTurnAnUpstreamFailureIntoZeroMetrics() {
        when(reader.query(AnalyticsQueries.REACH, 1, day, day.plusDays(30), AdminAnalyticsDtos.Reach.class))
                .thenThrow(AnalyticsReader.unavailable());
        for (int i=0; i<3; i++) assertThatThrownBy(() -> service.dashboard(1, day, day.plusDays(30)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(reader, times(3)).query(AnalyticsQueries.REACH, 1, day, day.plusDays(30), AdminAnalyticsDtos.Reach.class);
    }
}
