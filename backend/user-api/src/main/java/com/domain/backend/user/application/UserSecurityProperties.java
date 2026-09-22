package com.domain.backend.user.application;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.user-security")
public class UserSecurityProperties {

    private static final String DEFAULT_ACCESS_TOKEN_SECRET = "local-development-access-token-secret-change-me";
    private static final String EXAMPLE_ACCESS_TOKEN_SECRET = "replace-with-at-least-32-random-characters";

    private String cookieName = "OTT_USER_SESSION";
    private Duration sessionTtl = Duration.ofDays(7);
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private String accessTokenSecret;
    private Duration playbackSessionTtl = Duration.ofHours(6);
    private boolean cookieSecure = false;
    private String sameSite = "Lax";
    private int loginMaxAttemptsPerMinute = 10;
    private boolean localTestUserEnabled = false;
    private String localTestUserPassword;

    @PostConstruct
    void validate() {
        if (accessTokenSecret == null || accessTokenSecret.isBlank()
                || DEFAULT_ACCESS_TOKEN_SECRET.equals(accessTokenSecret)
                || EXAMPLE_ACCESS_TOKEN_SECRET.equals(accessTokenSecret)
                || accessTokenSecret.length() < 32) {
            throw new IllegalStateException("app.user-security.access-token-secret must be set to a non-default value with at least 32 characters");
        }
    }

    public String getCookieName() { return cookieName; }
    public void setCookieName(String cookieName) { this.cookieName = cookieName; }
    public Duration getSessionTtl() { return sessionTtl; }
    public void setSessionTtl(Duration sessionTtl) { this.sessionTtl = sessionTtl; }
    public Duration getAccessTokenTtl() { return accessTokenTtl; }
    public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }
    public String getAccessTokenSecret() { return accessTokenSecret; }
    public void setAccessTokenSecret(String accessTokenSecret) { this.accessTokenSecret = accessTokenSecret; }
    public Duration getPlaybackSessionTtl() { return playbackSessionTtl; }
    public void setPlaybackSessionTtl(Duration playbackSessionTtl) { this.playbackSessionTtl = playbackSessionTtl; }
    public boolean isCookieSecure() { return cookieSecure; }
    public void setCookieSecure(boolean cookieSecure) { this.cookieSecure = cookieSecure; }
    public String getSameSite() { return sameSite; }
    public void setSameSite(String sameSite) { this.sameSite = sameSite; }
    public int getLoginMaxAttemptsPerMinute() { return loginMaxAttemptsPerMinute; }
    public void setLoginMaxAttemptsPerMinute(int loginMaxAttemptsPerMinute) { this.loginMaxAttemptsPerMinute = loginMaxAttemptsPerMinute; }
    public boolean isLocalTestUserEnabled() { return localTestUserEnabled; }
    public void setLocalTestUserEnabled(boolean localTestUserEnabled) { this.localTestUserEnabled = localTestUserEnabled; }
    public String getLocalTestUserPassword() { return localTestUserPassword; }
    public void setLocalTestUserPassword(String localTestUserPassword) { this.localTestUserPassword = localTestUserPassword; }
}
