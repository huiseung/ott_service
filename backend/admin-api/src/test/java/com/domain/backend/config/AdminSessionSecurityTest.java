package com.domain.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.domain.backend.content.application.AdminContentService;
import com.domain.backend.content.presentation.AdminContentController;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitConfig(AdminSessionSecurityTest.Config.class)
@WebAppConfiguration
@TestPropertySource(properties = {"app.security.admin-username=test-admin", "app.security.admin-password=test-only"})
class AdminSessionSecurityTest {
    @Configuration @EnableWebMvc
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    @Import({SecurityConfig.class, AdminAuthController.class, AdminContentController.class})
    static class Config {
        @Bean AdminContentService contentService() { return mock(AdminContentService.class); }
    }
    @Autowired WebApplicationContext context;
    @Autowired AdminContentService contentService;
    MockMvc mvc;
    static final String CONTENT = """
            {"type":"MOVIE","status":"DRAFT","originalCountry":"KR","originalLanguage":"ko"}
            """;

    @BeforeEach void setup() {
        reset(contentService);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    String csrf(MockHttpSession session) throws Exception {
        var result = mvc.perform(get("/api/admin/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    MockHttpSession login() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(post("/api/admin/auth/login").session(session)
                .header("X-CSRF-TOKEN", csrf(session))
                .param("username", "test-admin").param("password", "test-only"))
                .andExpect(status().isNoContent());
        return session;
    }

    @Test void anonymousAndWrongCredentialsCannotAccessAdmin() throws Exception {
        mvc.perform(get("/api/admin/auth/session")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/contents")).andExpect(status().isUnauthorized());
        var session = new MockHttpSession();
        mvc.perform(post("/api/admin/auth/login").session(session)
                .header("X-CSRF-TOKEN", csrf(session))
                .param("username", "test-admin").param("password", "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/auth/session").session(session)).andExpect(status().isUnauthorized());
        verifyNoInteractions(contentService);
    }

    @Test void sessionSurvivesRequestsAndContentCreationRequiresCsrf() throws Exception {
        var session = login();
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/admin/auth/session").session(session))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("test-admin"))
                    .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"));
        }
        mvc.perform(post("/api/admin/contents").session(session).contentType("application/json").content(CONTENT))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        verifyNoInteractions(contentService);
        mvc.perform(post("/api/admin/contents").session(session).header("X-CSRF-TOKEN", csrf(session))
                .contentType("application/json").content(CONTENT)).andExpect(status().isCreated());
        verify(contentService).create(any());
    }

    @Test void loginAndLogoutRequireCsrfAndLogoutInvalidatesSession() throws Exception {
        mvc.perform(post("/api/admin/auth/login").param("username", "test-admin").param("password", "test-only"))
                .andExpect(status().isForbidden());
        var session = login();
        mvc.perform(post("/api/admin/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/auth/logout").session(session).header("X-CSRF-TOKEN", csrf(session)))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/admin/auth/session")).andExpect(status().isUnauthorized());
    }

    @Test void patchPreflightAllowsSessionAndCsrfHeader() throws Exception {
        mvc.perform(options("/api/admin/media-versions/1")
                .header("Origin", "http://localhost:3001")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}
