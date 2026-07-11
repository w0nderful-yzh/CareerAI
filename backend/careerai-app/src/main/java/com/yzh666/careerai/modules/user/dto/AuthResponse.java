package com.yzh666.careerai.modules.user.dto;

public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresInSeconds,
    CurrentUserDTO user
) {
}
