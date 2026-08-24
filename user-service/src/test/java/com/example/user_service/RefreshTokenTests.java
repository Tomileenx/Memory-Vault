package com.example.user_service;

import com.example.user_service.config.JWTService;
import com.example.user_service.config.UserPrincipal;
import com.example.user_service.exception.TokenExpired;
import com.example.user_service.refreshToken.*;
import com.example.user_service.userAccount.UserAccount;
import com.example.user_service.userAccount.UserAccountRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenTests {

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Mock
    private RefreshTokenRepo refreshTokenRepo;

    @Mock
    private UserAccountRepo userAccountRepo;

    @Mock
    private JWTService jwtService;

    @Test
    void shouldCreateRefreshTokenSuccessfully() {

        UserAccount user = UserAccount.builder()
                .email("john@gmail.com")
                .build();

        RefreshToken savedToken = RefreshToken.builder()
                .tokenHash("generated-token")
                .userAccount(user)
                .expiryDate(Instant.now().plus(Duration.ofDays(1)))
                .lastActivity(Instant.now())
                .build();

        when(refreshTokenRepo.save(any(RefreshToken.class)))
                .thenReturn(savedToken);

        RefreshTokenResponse response =
                refreshTokenService.createRefreshToken(user);

        assertNotNull(response);
        assertNotNull(response.refreshToken());
        assertEquals(savedToken.getExpiryDate(), response.expiryDate());

        verify(refreshTokenRepo).save(any(RefreshToken.class));
    }

    @Test
    void shouldVerifyRefreshTokenSuccessfully() {

        UserAccount user = UserAccount.builder()
                .email("john@gmail.com")
                .build();

        RefreshToken token = RefreshToken.builder()
                .tokenHash("refresh-token")
                .userAccount(user)
                .expiryDate(Instant.now().plus(Duration.ofDays(1)))
                .lastActivity(Instant.now())
                .build();

        RefreshTokenRequest request =
                new RefreshTokenRequest("refresh-token");

        when(refreshTokenRepo.findByTokenHash(request.refreshToken()))
                .thenReturn(Optional.of(token));

        when(jwtService.generateToken(any(UserPrincipal.class)))
                .thenReturn("access-token");

        VerifyRefreshTokenResponse response =
                refreshTokenService.verifyToken(request);

        assertEquals("access-token", response.accessToken());

        verify(jwtService).generateToken(any(UserPrincipal.class));
        verify(refreshTokenRepo).save(token);
    }

    @Test
    void shouldThrowWhenRefreshTokenExpired() {

        UserAccount user = UserAccount.builder().build();

        RefreshToken token = RefreshToken.builder()
                .tokenHash("refresh-token")
                .userAccount(user)
                .expiryDate(Instant.now().minus(Duration.ofMinutes(1)))
                .lastActivity(Instant.now())
                .build();

        RefreshTokenRequest request =
                new RefreshTokenRequest("refresh-token");

        when(refreshTokenRepo.findByTokenHash(request.refreshToken()))
                .thenReturn(Optional.of(token));

        assertThrows(
                TokenExpired.class,
                () -> refreshTokenService.verifyToken(request)
        );

        verify(refreshTokenRepo).delete(token);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void shouldLogoutSuccessfully() {

        UserAccount user = UserAccount.builder()
                .email("john@gmail.com")
                .build();

        when(userAccountRepo.findByEmailIgnoreCase(user.getEmail()))
                .thenReturn(Optional.of(user));

        refreshTokenService.logout(user.getEmail());

        verify(refreshTokenRepo).deleteByUserAccount(user);
    }
}
