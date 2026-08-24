package com.example.user_service;

import com.example.events.EmailVerificationSuccessfulEvent;
import com.example.events.PasswordResetTokenEvent;
import com.example.user_service.exception.BadRequest;
import com.example.user_service.exception.NotFound;
import com.example.user_service.passwordResetToken.PasswordResetTokenRepo;
import com.example.user_service.passwordResetToken.PasswordResetTokenService;
import com.example.user_service.userAccount.UserAccount;
import com.example.user_service.userAccount.UserAccountRepo;
import com.example.user_service.verificationToken.VerificationToken;
import com.example.user_service.verificationToken.VerificationTokenRepo;
import com.example.user_service.verificationToken.VerificationTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmailVerificationTests {

    @InjectMocks
    private VerificationTokenService verificationTokenService;

    @Mock
    private VerificationTokenRepo verificationTokenRepo;

    @Mock
    private UserAccountRepo userAccountRepo;

    @Mock
    private KafkaTemplate<String, EmailVerificationSuccessfulEvent> kafkaTemplate;

    @Test
    void shouldVerifyEmailSuccessfully() {
        String token = "verification-token";

        UserAccount user = UserAccount.builder()
                .email("john@gmail.com")
                .fullName("John Doe")
                .username("JohnnyDoe")
                .verified(false)
                .build();

        VerificationToken verificationToken = VerificationToken.builder()
                .token(token)
                .userAccount(user)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
                .build();

        when(verificationTokenRepo.findByToken(token))
                .thenReturn(Optional.of(verificationToken));

        verificationTokenService.verifyEmail(token);

        assertTrue(user.isVerified());

        verify(userAccountRepo).save(user);
        verify(verificationTokenRepo).delete(verificationToken);
        verify(kafkaTemplate).send(
                eq("email.verification.successful"),
                eq(user.getEmail()),
                any(EmailVerificationSuccessfulEvent.class)
        );
    }

    @Test
    void shouldThrowWhenTokenIsInvalid() {

        String token = "invalid-token";

        when(verificationTokenRepo.findByToken(token))
                .thenReturn(Optional.empty());

        NotFound exception = assertThrows(
                NotFound.class,
                () -> verificationTokenService.verifyEmail(token)
        );

        assertEquals("Invalid token", exception.getMessage());

        verify(userAccountRepo, never()).save(any());
        verify(verificationTokenRepo, never()).delete(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void shouldThrowWhenTokenExpired() {

        String token = "verification-token";

        UserAccount user = UserAccount.builder()
                .email("john@gmail.com")
                .fullName("John Doe")
                .username("JohnnyDoe")
                .verified(false)
                .build();

        VerificationToken verificationToken = VerificationToken.builder()
                .token(token)
                .userAccount(user)
                .expiresAt(Instant.now().minus(Duration.ofMinutes(1)))
                .build();

        when(verificationTokenRepo.findByToken(token))
                .thenReturn(Optional.of(verificationToken));

        BadRequest exception = assertThrows(
                BadRequest.class,
                () -> verificationTokenService.verifyEmail(token)
        );

        assertEquals("Verification token expired", exception.getMessage());

        verify(userAccountRepo, never()).save(any());
        verify(verificationTokenRepo, never()).delete(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void shouldThrowWhenEmailVerified() {
        String token = "verification-token";

        UserAccount user = UserAccount.builder()
                .email("john@gmail.com")
                .verified(true)
                .build();

        VerificationToken verificationToken = VerificationToken.builder()
                .token(token)
                .userAccount(user)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
                .build();

        when(verificationTokenRepo.findByToken(token))
                .thenReturn(Optional.of(verificationToken));

        BadRequest exception = assertThrows(
                BadRequest.class,
                () -> verificationTokenService.verifyEmail(token)
        );

        assertEquals("Email already verified", exception.getMessage());

        verify(userAccountRepo, never()).save(any());
        verify(verificationTokenRepo, never()).delete(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }
}
