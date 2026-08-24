package com.example.user_service;

import com.example.user_service.auth.AuthService;
import com.example.user_service.auth.LoginRequest;
import com.example.user_service.auth.LoginResponse;
import com.example.user_service.config.JWTService;
import com.example.user_service.config.UserPrincipal;
import com.example.user_service.exception.BadRequest;
import com.example.user_service.refreshToken.RefreshTokenResponse;
import com.example.user_service.refreshToken.RefreshTokenService;
import com.example.user_service.userAccount.UserAccount;
import com.example.user_service.userAccount.UserAccountRepo;
import com.example.user_service.verificationToken.VerificationTokenRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class AuthenticationTests {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserAccountRepo userAccountRepo;

    @Mock
    private JWTService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authManager;

    @Mock
    private VerificationTokenRepo verificationTokenRepo;

    @Test
    void shouldLoginSuccessfully() {

        LoginRequest request = new LoginRequest(
                "JohnDoe@gmail.com",
                "passwordJon@1#"
        );

        UserAccount user = UserAccount.builder()
                .fullName("John Doe")
                .username("JohnnyDoe")
                .email("JohnDoe@gmail.com")
                .verified(true)
                .build();

        UserPrincipal principal = new UserPrincipal(user);

        Authentication authentication = mock(Authentication.class);

        RefreshTokenResponse refreshToken =
                new RefreshTokenResponse(
                        "refresh-token",
                        Instant.now().plus(Duration.ofDays(7))
                );

        when(authManager.authenticate(any(Authentication.class)))
                .thenReturn(authentication);

        when(authentication.getPrincipal())
                .thenReturn(principal);

        when(jwtService.generateToken(principal))
                .thenReturn("jwt-token");

        when(refreshTokenService.createRefreshToken(user))
                .thenReturn(refreshToken);

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("John Doe", response.fullName());
        assertEquals("JohnnyDoe", response.username());
        assertEquals("jwt-token", response.token());
        assertEquals("refresh-token", response.refreshToken());

        verify(authManager).authenticate(any(Authentication.class));
        verify(jwtService).generateToken(principal);
        verify(refreshTokenService).createRefreshToken(user);
    }

    @Test
    void shouldThrowWhenUserIsNotVerified() {
        LoginRequest request = new LoginRequest(
                "JohnDoe@gmail.com",
                "passwordJon@1#"
        );

        UserAccount user = UserAccount.builder()
                .email("JohnDoe@gmail.com")
                .verified(false)
                .build();

        UserPrincipal userPrincipal = new UserPrincipal(user);

        Authentication authentication = mock(Authentication.class);

        when(authManager.authenticate(any(Authentication.class)))
                .thenReturn(authentication);

        when(authentication.getPrincipal())
                .thenReturn(userPrincipal);

        BadRequest exception = assertThrows(BadRequest.class,
                () -> authService.login(request));

        assertEquals("Please verify your email first", exception.getMessage());

        verify(jwtService, never()).generateToken(any());
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void shouldThrowWhenCredentialsAreInvalid() {
        LoginRequest request = new LoginRequest(
                "JohnDoe@gmail.com",
                "passwordJon@1#"
        );

        when(authManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class,
                () -> authService.login(request));

        verify(jwtService, never()).generateToken(any());
        verify(refreshTokenService, never()).createRefreshToken(any());
    }
}
