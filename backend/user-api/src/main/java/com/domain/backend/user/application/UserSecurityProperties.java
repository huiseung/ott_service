package com.domain.backend.user.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.user-security")
public class UserSecurityProperties {

    private String cookieName = "OTT_USER_SESSION";
    private Duration sessionTtl = Duration.ofDays(7);
    private Duration playbackSessionTtl = Duration.ofHours(6);
    private boolean cookieSecure = false;
    private String sameSite = "Lax";
    private int loginMaxAttemptsPerMinute = 10;
    private boolean localTestUserEnabled = false;
    private String localTestUserPassword;

    public String getCookieName() { return cookieName; }
    public void setCookieName(String cookieName) { this.cookieName = cookieName; }
    public Duration getSessionTtl() { return sessionTtl; }
    public void setSessionTtl(Duration sessionTtl) { this.sessionTtl = sessionTtl; }
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
