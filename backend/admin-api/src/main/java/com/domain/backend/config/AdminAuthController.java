package com.domain.backend.config;

import java.security.Principal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrf) {
        return new CsrfResponse(csrf.getHeaderName(), csrf.getToken());
    }

    @GetMapping("/session")
    public SessionResponse session(Principal principal) {
        return new SessionResponse(principal.getName());
    }

    public record CsrfResponse(String headerName, String token) {}
    public record SessionResponse(String username) {}
}
