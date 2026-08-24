package com.example.user_service.auth;

public record LoginResponse(
        String fullName,
        String username,
        String token,
        String refreshToken
) {
}
