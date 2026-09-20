package com.domain.backend.user.application;

import jakarta.validation.constraints.NotBlank;

public final class UserAuthDtos {

    private UserAuthDtos() {
    }

    public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {
    }

    public record LoginResponse(Long userId, String loginId, String displayName) {
    }

    public record CurrentUserResponse(Long userId, String loginId, String displayName) {
    }
}
