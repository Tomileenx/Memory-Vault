package com.example.user_service.rateLimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RateLimitService {
    private final RedisTemplate<String, String> redisTemplate;

    public boolean allowRequest(
            String identifier,
            RateLimitOperation operation
    ) {
        String key = "rate_limit:"
                + operation.name().toLowerCase()
                + ":"
                + identifier;

        RateLimitPolicy policy = resolvePolicy(operation);

        Long requests = redisTemplate.opsForValue().increment(key);

        if (requests == null) {
            return false;
        }

        if (requests == 1) {
            return redisTemplate.expire(key, policy.duration());
        }

        return requests <= policy.limit();
    }

    private RateLimitPolicy resolvePolicy(RateLimitOperation operation) {
        return switch (operation) {
            case REGISTER ->
                new RateLimitPolicy(3, Duration.ofMinutes(1));
            case LOGIN ->
                new RateLimitPolicy(5, Duration.ofMinutes(1));
            case VERIFICATION ->
                new RateLimitPolicy(10, Duration.ofHours(1));
            case RESEND_VERIFICATION ->
                new RateLimitPolicy(5, Duration.ofHours(1));
            case FORGOT_PASSWORD ->
                new RateLimitPolicy(3, Duration.ofMinutes(15));
            default ->
                new RateLimitPolicy(100, Duration.ofMinutes(1));
        };
    }
}
