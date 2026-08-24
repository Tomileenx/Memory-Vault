package com.example.user_service.refreshToken;

import java.time.Instant;

public record RefreshTokenResponse(
        String refreshToken,
        Instant expiryDate
) {
}
