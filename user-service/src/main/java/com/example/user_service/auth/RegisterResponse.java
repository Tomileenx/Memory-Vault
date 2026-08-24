package com.example.user_service.auth;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record RegisterResponse(
        UUID id,
        String fullName,
        String email,
        String password,
        Instant createdAt,
        String message
) {
}
