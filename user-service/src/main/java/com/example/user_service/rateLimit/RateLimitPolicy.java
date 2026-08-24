package com.example.user_service.rateLimit;

import java.time.Duration;

public record RateLimitPolicy(
        int limit,
        Duration duration
) {
}
