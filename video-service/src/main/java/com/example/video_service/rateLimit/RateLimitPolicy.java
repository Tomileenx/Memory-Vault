package com.example.video_service.rateLimit;

import software.amazon.awssdk.services.s3.endpoints.internal.Value;

import java.time.Duration;

public record RateLimitPolicy(
     int limit,
     Duration duration
) {
}
