package com.domain.backend.user.application;

import com.domain.backend.user.infrastructure.persistence.AppUserRepository;
import com.domain.backend.user.infrastructure.persistence.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class UserSessionAuthenticationFilter extends OncePerRequestFilter {

    private final UserSecurityProperties properties;
    private final UserSessionRepository sessionRepository;
    private final AppUserRepository userRepository;
    private final TokenHasher tokenHasher;

    public UserSessionAuthenticationFilter(UserSecurityProperties properties, UserSessionRepository sessionRepository,
                                           AppUserRepository userRepository, TokenHasher tokenHasher) {
        this.properties = properties;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.tokenHasher = tokenHasher;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = cookieValue(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            sessionRepository.findBySessionTokenHashAndInvalidatedAtIsNullAndExpiresAtAfter(tokenHasher.sha256(token), Instant.now())
                    .flatMap(session -> userRepository.findById(session.getUserId())
                            .filter(user -> user.isEnabled())
                            .map(user -> {
                                sessionRepository.touch(session.getId(), Instant.now());
                                var principal = new UserPrincipal(user.getId(), user.getLoginId(), user.getDisplayName());
                                return new UsernamePasswordAuthenticationToken(
                                        principal,
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                                );
                            }))
                    .ifPresent(authentication -> SecurityContextHolder.getContext().setAuthentication(authentication));
        }
        filterChain.doFilter(request, response);
    }

    private String cookieValue(HttpServletRequest request) {
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
}
