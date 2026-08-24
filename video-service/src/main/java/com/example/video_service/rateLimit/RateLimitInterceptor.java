package com.example.video_service.rateLimit;

import com.example.video_service.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

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
        switch (operation) {
            case UPLOAD_URL:
            case COMPLETE_UPLOAD:
                return request.getRemoteAddr();

            default:
                Authentication authentication = SecurityContextHolder
                        .getContext()
                        .getAuthentication();

                if (authentication == null || !authentication.isAuthenticated()) {
                    return request.getRemoteAddr();
                }

                return authentication.getName();
        }
    }

    private RateLimitOperation resolveOperation(
            HttpServletRequest request
    ) {
        String uri = request.getRequestURI();

        return switch (uri) {

            case "/api/v1/memVault/upload-url" ->
                    RateLimitOperation.UPLOAD_URL;

            case "/api/v1/memVault/{videoId}/upload/complete" ->
                    RateLimitOperation.COMPLETE_UPLOAD;

            default ->
                    RateLimitOperation.DEFAULT;
        };
    }
}
