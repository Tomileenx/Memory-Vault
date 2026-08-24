package com.example.user_service;


import com.example.user_service.exception.TooManyRequestsException;
import com.example.user_service.rateLimit.RateLimitInterceptor;
import com.example.user_service.rateLimit.RateLimitOperation;
import com.example.user_service.rateLimit.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RateLimitTests {

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private RateLimitInterceptor interceptor;

    @Test
    void shouldAllowRequestWhenRateLimitNotExceeded() throws Exception {

        when(request.getRequestURI())
                .thenReturn("/api/v1/memVault/login");

        when(request.getRemoteAddr())
                .thenReturn("127.0.0.1");

        when(rateLimitService.allowRequest(
                "127.0.0.1",
                RateLimitOperation.LOGIN
        )).thenReturn(true);

        boolean result = interceptor.preHandle(
                request,
                response,
                new Object()
        );

        assertTrue(result);

        verify(rateLimitService).allowRequest(
                "127.0.0.1",
                RateLimitOperation.LOGIN
        );
    }

    @Test
    void shouldThrowTooManyRequestsWhenLimitExceeded() {

        when(request.getRequestURI())
                .thenReturn("/api/v1/memVault/login");

        when(request.getRemoteAddr())
                .thenReturn("127.0.0.1");

        when(rateLimitService.allowRequest(
                "127.0.0.1",
                RateLimitOperation.LOGIN
        )).thenReturn(false);

        TooManyRequestsException exception =
                assertThrows(
                        TooManyRequestsException.class,
                        () -> interceptor.preHandle(
                                request,
                                response,
                                new Object()
                        )
                );

        assertEquals(
                "Too many requests. Please try again later.",
                exception.getMessage()
        );

        verify(rateLimitService).allowRequest(
                "127.0.0.1",
                RateLimitOperation.LOGIN
        );
    }

    @Test
    void shouldUseAuthenticatedUsernameForDefaultEndpoints() throws Exception {

        Authentication authentication = mock(Authentication.class);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("john@gmail.com");

        when(request.getRequestURI())
                .thenReturn("/api/v1/memVault/profile");

        when(rateLimitService.allowRequest(
                "john@gmail.com",
                RateLimitOperation.DEFAULT
        )).thenReturn(true);

        boolean result = interceptor.preHandle(
                request,
                response,
                new Object()
        );

        assertTrue(result);

        verify(rateLimitService).allowRequest(
                "john@gmail.com",
                RateLimitOperation.DEFAULT
        );

        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldUseIpAddressWhenUserNotAuthenticated() throws Exception {

        when(request.getRequestURI())
                .thenReturn("/api/v1/memVault/profile");

        when(request.getRemoteAddr())
                .thenReturn("127.0.0.1");

        SecurityContextHolder.clearContext();

        when(rateLimitService.allowRequest(
                "127.0.0.1",
                RateLimitOperation.DEFAULT
        )).thenReturn(true);

        boolean result = interceptor.preHandle(
                request,
                response,
                new Object()
        );

        assertTrue(result);

        verify(rateLimitService).allowRequest(
                "127.0.0.1",
                RateLimitOperation.DEFAULT
        );
    }
}
