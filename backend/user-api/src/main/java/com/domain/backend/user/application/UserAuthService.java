package com.domain.backend.user.application;

import com.domain.backend.user.application.UserAuthDtos.LoginRequest;
import com.domain.backend.user.application.UserAuthDtos.LoginResponse;
import com.domain.backend.user.domain.UserSession;
import com.domain.backend.user.infrastructure.persistence.AppUserRepository;
import com.domain.backend.user.infrastructure.persistence.UserSessionRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserAuthService {

    private final AppUserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenHasher tokenHasher;
    private final UserSecurityProperties properties;
    private final LoginRateLimiter rateLimiter;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserAuthService(AppUserRepository userRepository, UserSessionRepository sessionRepository,
                           PasswordEncoder passwordEncoder, TokenHasher tokenHasher,
                           UserSecurityProperties properties, LoginRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenHasher = tokenHasher;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public LoginResult login(LoginRequest request, HttpServletRequest servletRequest) {
        String rateLimitKey = servletRequest.getRemoteAddr() + ":" + request.loginId();
        if (!rateLimiter.allow(rateLimitKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts");
        }
        var user = userRepository.findByLoginId(request.loginId())
                .filter(candidate -> candidate.isEnabled()
                        && passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid login credentials"));

        String token = randomToken();
        sessionRepository.save(new UserSession(
                tokenHasher.sha256(token),
                user.getId(),
                Instant.now().plus(properties.getSessionTtl())
        ));
        return new LoginResult(
                new LoginResponse(user.getId(), user.getLoginId(), user.getDisplayName()),
                sessionCookie(token, properties.getSessionTtl().toSeconds()).toString()
        );
    }

    public String logoutCookieHeader() {
        return sessionCookie("", 0).toString();
    }

    public void logout(HttpServletRequest request) {
        String token = findCookie(request);
        if (token != null) {
            sessionRepository.invalidate(tokenHasher.sha256(token), Instant.now());
        }
    }

    private ResponseCookie sessionCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(properties.getCookieName(), token)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite(properties.getSameSite())
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }

    private String findCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (properties.getCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record LoginResult(LoginResponse response, String setCookieHeader) {

        public HttpHeaders headers() {
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.SET_COOKIE, setCookieHeader);
            return headers;
        }
    }
}
