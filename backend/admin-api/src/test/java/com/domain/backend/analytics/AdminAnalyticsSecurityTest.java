package com.domain.backend.analytics;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.domain.backend.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitConfig(AdminAnalyticsSecurityTest.Config.class)
@WebAppConfiguration
@TestPropertySource(properties={"app.security.admin-username=test-admin", "app.security.admin-password=test-only"})
class AdminAnalyticsSecurityTest {
    @Configuration @EnableWebMvc
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    @Import({SecurityConfig.class, AdminAnalyticsController.class})
    static class Config { @Bean AdminAnalyticsService analytics() { return mock(AdminAnalyticsService.class); } }
    @Autowired WebApplicationContext context;
    @Autowired AdminAnalyticsService service;
    MockMvc mvc;
    final String path = "/api/admin/analytics/contents/10?from=2026-09-01&to=2026-09-25";
    @BeforeEach void setup() { reset(service); mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @Test void anonymousAndUserCannotReadBusinessAnalytics() throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(user("viewer").roles("USER"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void adminReadsWithoutCachingAndMalformedDatesAreRejected() throws Exception {
        mvc.perform(get(path).with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(path.replace("2026-09-01", "bad-date")).with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
    }
}
