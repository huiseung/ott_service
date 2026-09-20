package com.domain.backend.user.application;

public record UserPrincipal(Long userId, String loginId, String displayName) {
}
