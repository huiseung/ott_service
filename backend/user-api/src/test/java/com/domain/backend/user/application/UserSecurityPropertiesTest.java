package com.domain.backend.user.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserSecurityPropertiesTest {

    @Test
    void rejectsDefaultAccessTokenSecret() {
        var properties = new UserSecurityProperties();
        properties.setAccessTokenSecret("local-development-access-token-secret-change-me");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access-token-secret");
    }

    @Test
    void rejectsExampleAccessTokenSecret() {
        var properties = new UserSecurityProperties();
        properties.setAccessTokenSecret("replace-with-at-least-32-random-characters");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access-token-secret");
    }

    @Test
    void acceptsStrongAccessTokenSecret() {
        var properties = new UserSecurityProperties();
        properties.setAccessTokenSecret("0123456789abcdef0123456789abcdef");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
