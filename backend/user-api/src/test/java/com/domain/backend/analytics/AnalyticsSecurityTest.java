package com.domain.backend.analytics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.domain.backend.user.application.AccessTokenService;
import com.domain.backend.user.application.UserPrincipal;
import com.domain.backend.user.config.UserSecurityConfig;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitConfig(AnalyticsSecurityTest.Config.class)
@WebAppConfiguration
class AnalyticsSecurityTest {
    @Configuration
    @EnableWebMvc
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    @Import({UserSecurityConfig.class, AnalyticsController.class})
    static class Config {
        @Bean AnalyticsCollector collector() { return mock(AnalyticsCollector.class); }
        @Bean AccessTokenService tokens() { return mock(AccessTokenService.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired AnalyticsCollector collector;
    @Autowired AccessTokenService tokens;
    MockMvc mvc;
    static final String BODY = """
            {"events":[{"eventId":"00000000-0000-0000-0000-000000000001","eventType":"CONTENT_CLICK",
            "eventVersion":1,"occurredAt":"2026-09-23T00:00:00Z","anonymousId":"anon","sessionId":"visit",
            "producer":"user-web","platform":"WEB","contentId":1,"userId":999,"payload":{}}]}
            """;

    @BeforeEach
    void setup() {
        reset(collector, tokens);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(collector.collect(any(), any())).thenReturn(CompletableFuture.completedFuture(new AnalyticsDtos.Accepted(List.of())));
    }

    @Test
    void authenticatedIdentityComesFromBearerInsteadOfJson() throws Exception {
        var principal = new UserPrincipal(7L, "viewer", "Viewer");
        when(tokens.verify("test-token")).thenReturn(principal);
        var result = mvc.perform(post("/api/analytics/events/batch").header("Authorization", "Bearer test-token")
                        .contentType("application/json").content(BODY))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isAccepted());
        verify(collector).collect(any(), eq(principal));
    }

    @Test
    void anonymousBehaviorAndAllowedOriginPreflightWork() throws Exception {
        var result = mvc.perform(post("/api/analytics/events/batch").contentType("application/json").content(BODY))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isAccepted());
        verify(collector).collect(any(), isNull());
        mvc.perform(options("/api/analytics/events/batch").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        assertThat(context.getBean(UserSecurityConfig.class)).isNotNull();
    }
}
