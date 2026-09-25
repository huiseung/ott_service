package com.domain.backend.analytics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AnalyticsHttpTest {
    final AnalyticsCollector collector = mock(AnalyticsCollector.class);

    @Test
    void acceptsAnonymousBatchOnlyAfterPublicationCompletes() throws Exception {
        when(collector.collect(any(), isNull())).thenReturn(CompletableFuture.completedFuture(new AnalyticsDtos.Accepted(List.of())));
        var mvc = MockMvcBuilders.standaloneSetup(new AnalyticsController(collector))
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).build();
        var result = mvc.perform(post("/api/analytics/events/batch").contentType("application/json").content("""
                {"events":[{"eventId":"00000000-0000-0000-0000-000000000001","eventType":"CONTENT_CLICK",
                "eventVersion":1,"occurredAt":"2026-09-23T00:00:00Z","anonymousId":"anon","sessionId":"visit",
                "producer":"user-web","platform":"WEB","contentId":1,"payload":{"surface":"home"}}]}
                """)).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isAccepted());
    }

    @Test
    void rejectsEmptyBatchAndBackendOnlyPaymentEvents() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AnalyticsController(collector))
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(post("/api/analytics/events/batch").contentType("application/json").content("{\"events\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/analytics/events/batch").contentType("application/json")
                        .content("{\"events\":[{\"eventType\":\"SUBSCRIPTION_ACTIVATED\"}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(collector);
    }

    @Test
    void failedPublicationReturns503InsteadOfAcceptingLostEvents() throws Exception {
        when(collector.collect(any(), isNull())).thenReturn(CompletableFuture.failedFuture(
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)));
        var mvc = MockMvcBuilders.standaloneSetup(new AnalyticsController(collector))
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).build();
        var result = mvc.perform(post("/api/analytics/events/batch").contentType("application/json").content("""
                {"events":[{"eventId":"00000000-0000-0000-0000-000000000001","eventType":"CONTENT_CLICK",
                "eventVersion":1,"occurredAt":"2026-09-23T00:00:00Z","anonymousId":"anon","sessionId":"visit",
                "producer":"user-web","platform":"WEB","contentId":1,"payload":{}}]}
                """)).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isServiceUnavailable());
    }

    @Test
    void limitsUnknownLengthBodiesBeforeJsonParsing() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/analytics/events/batch") {
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setContent(new byte[AnalyticsRequestLimitFilter.MAX_BYTES + 1]);
        var response = new MockHttpServletResponse();
        new AnalyticsRequestLimitFilter().doFilter(request, response,
                (req, res) -> { throw new AssertionError("Oversized request reached controller"); });
        assertThat(response.getStatus()).isEqualTo(413);
    }
}
