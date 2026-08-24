package com.example.video_service.rateLimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitService {
    private final RedisTemplate<String, String> redisTemplate;

    // Identify who's making the request & type of operation
    public boolean allowRequest(
            String identifier,
            RateLimitOperation operation
    ) {
        // Generate the redis key
        String key = "rate_limit:" +
                operation.name().toLowerCase()
                + ":"
                + identifier;

        // Resolve the policy for each operation (limit & duration)
        RateLimitPolicy policy = resolvePolicy(operation);

        // Increment the redis counter
        Long requests = redisTemplate.opsForValue().increment(key);

        // If increment fails reject request
        if (requests == null) {
            return false;
        }

        // Setting the TTL for a request if there's a request
        if (requests == 1) {
            return redisTemplate.expire(key, policy.duration());
        }

        // Checks if requests are less than the request policy limit
        return requests <= policy.limit();
    }

    public RateLimitPolicy resolvePolicy(RateLimitOperation operation) {
        return switch (operation) {
            case UPLOAD_URL ->
                new RateLimitPolicy(10, Duration.ofMinutes(1));
            case COMPLETE_UPLOAD ->
                new RateLimitPolicy(15, Duration.ofMinutes(1));
            default ->
                new RateLimitPolicy(5, Duration.ofMinutes(1));
        };
    }
}
