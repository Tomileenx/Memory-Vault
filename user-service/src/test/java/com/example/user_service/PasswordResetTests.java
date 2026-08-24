package com.example.user_service;


import com.example.events.PasswordResetTokenEvent;
import com.example.user_service.exception.BadRequest;
import com.example.user_service.exception.NotFound;
import com.example.user_service.exception.Unauthorized;
import com.example.user_service.passwordResetToken.*;
import com.example.user_service.userAccount.UserAccount;
import com.example.user_service.userAccount.UserAccountRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;


import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class PasswordResetTests {

    @InjectMocks
    private PasswordResetTokenService passwordResetTokenService;

    @Mock
    private UserAccountRepo userAccountRepo;

    @Mock
    private PasswordResetTokenRepo passwordResetTokenRepo;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private KafkaTemplate<String, PasswordResetTokenEvent> kafkaTemplate;

    @Test
    void shouldGeneratePasswordResetTokenSuccessfully() {
        ForgotPasswordRequest request = new ForgotPasswordRequest(
                "JohnDoe@gmail.com"
        );

        UserAccount user = UserAccount.builder()
                .email("JohnDoe@gmail.com")
                .build();

        when(userAccountRepo.findByEmailIgnoreCase(request.email()))
                .thenReturn(Optional.of(user));

        passwordResetTokenService.forgotPassword(request);

        verify(passwordResetTokenRepo).deleteAllByUserAccount(user);
        verify(passwordResetTokenRepo).save(any(PasswordResetToken.class));
        verify(kafkaTemplate).send(
                eq("password.reset.token"),
                eq(user.getEmail()),
                any(PasswordResetTokenEvent.class)
        );
    }

    @Test
    void shouldThrowWhenEmailDoesNotExist() {
        ForgotPasswordRequest request = new ForgotPasswordRequest(
                "JohnDoe@gmail.com"
        );

        when(userAccountRepo.findByEmailIgnoreCase(request.email()))
                .thenReturn(Optional.empty());

        assertThrows(NotFound.class,
                () -> passwordResetTokenService.forgotPassword(request));

        verify(passwordResetTokenRepo, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void shouldResetPasswordSuccessfully() {
        String token = "reset-token";

        ResetPasswordRequest request = new ResetPasswordRequest(
                "password@123",
                "password@123"
        );

        UserAccount user = UserAccount.builder()
                .password("old-password")
                .build();

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .userAccount(user)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
                .build();

        when(passwordResetTokenRepo.findByToken(token))
                .thenReturn(Optional.of(resetToken));

        when(passwordEncoder.encode(request.newPassword()))
                .thenReturn("encoded-password");

        passwordResetTokenService.resetPassword(
                token,
                request
        );

        assertEquals("encoded-password", user.getPassword());

        verify(passwordEncoder).encode(request.newPassword());
        verify(passwordResetTokenRepo).delete(resetToken);
    }

    @Test
    void shouldThrowWhenTokenExpired() {
        String token = "reset-token";

        UserAccount user = UserAccount.builder().build();

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .userAccount(user)
                .expiresAt(Instant.now().minus(Duration.ofMinutes(1)))
                .build();

        ResetPasswordRequest request = new ResetPasswordRequest(
                "password@123",
                "password@123"
        );

        when(passwordResetTokenRepo.findByToken(token))
                .thenReturn(Optional.of(resetToken));

        assertThrows(Unauthorized.class,
                () -> passwordResetTokenService.resetPassword(
                        token,
                        request
                ));

        verify(passwordEncoder, never()).encode(anyString());
        verify(passwordResetTokenRepo, never()).delete(any());
    }

    @Test
    void shouldThrowWhenPasswordsDoNotMatch() {
        String token = "reset-token";

        UserAccount user = UserAccount.builder().build();

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .userAccount(user)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
                .build();

        ResetPasswordRequest request = new ResetPasswordRequest(
                "password@123",
                "dPassword@123"
        );

        when(passwordResetTokenRepo.findByToken(token))
                .thenReturn(Optional.of(resetToken));

        BadRequest exception = assertThrows(
                BadRequest.class,
                () -> passwordResetTokenService.resetPassword(token, request)
        );

        assertEquals("Passwords do not match", exception.getMessage());

        verify(passwordEncoder, never()).encode(anyString());
        verify(passwordResetTokenRepo, never()).delete(any());
    }
}
