package com.example.streaming_service.rateLimit;

import com.example.streaming_service.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import static com.example.streaming_service.rateLimit.RateLimitOperation.GET_STREAMING_URL;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {
        RateLimitOperation operation = resolveOperation(request);

        if (operation == null) {
            return true;
        }

        String identifier = resolveIdentifier(request, operation);

        boolean allowed = rateLimitService.allowRequest(identifier, operation);

        if (!allowed) {
            throw new TooManyRequestsException(
                    "Too many requests. Please try again later."
            );
        }

        return true;
    }

    private String resolveIdentifier(
            HttpServletRequest request,
            RateLimitOperation operation
    ) {
        if (operation == GET_STREAMING_URL) {
            Authentication authentication = SecurityContextHolder
                    .getContext()
                    .getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                return request.getRemoteAddr();
            }

            return authentication.getName();
        }

        throw new IllegalArgumentException(
                "Unsupported rate limit operation: " + operation
        );
    }

    private RateLimitOperation resolveOperation(
            HttpServletRequest request
    ) {
        String uri = request.getRequestURI();

        if (uri.startsWith("/api/v1/memVault/stream/")) {
            return GET_STREAMING_URL;
        }

        return null;
    }
}
