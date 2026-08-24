package com.example.user_service.rateLimit;


import com.example.user_service.exception.TooManyRequestsException;
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
            case REGISTER:
            case LOGIN:
            case VERIFICATION:
            case RESEND_VERIFICATION:
            case FORGOT_PASSWORD:
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

            case "/api/v1/memVault/register" ->
                    RateLimitOperation.REGISTER;

            case "/api/v1/memVault/login" ->
                    RateLimitOperation.LOGIN;

            case "/api/v1/memVault/verify-email" ->
                    RateLimitOperation.VERIFICATION;

            case "/api/v1/memVault/resend-verification" ->
                    RateLimitOperation.RESEND_VERIFICATION;

            case "/api/v1/memVault/forgot-password" ->
            RateLimitOperation.FORGOT_PASSWORD;

            default ->
                    RateLimitOperation.DEFAULT;
        };
    }
}
