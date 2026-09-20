package com.domain.backend.user.presentation;

import com.domain.backend.user.application.UserAuthDtos.CurrentUserResponse;
import com.domain.backend.user.application.UserAuthDtos.LoginRequest;
import com.domain.backend.user.application.UserAuthDtos.LoginResponse;
import com.domain.backend.user.application.UserAuthService;
import com.domain.backend.user.application.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserAuthController {

    private final UserAuthService authService;

    public UserAuthController(UserAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        var result = authService.login(request, servletRequest);
        return ResponseEntity.ok()
                .headers(result.headers())
                .body(result.response());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authService.logoutCookieHeader())
                .build();
    }

    @GetMapping("/user/me")
    public CurrentUserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return new CurrentUserResponse(principal.userId(), principal.loginId(), principal.displayName());
    }
}
