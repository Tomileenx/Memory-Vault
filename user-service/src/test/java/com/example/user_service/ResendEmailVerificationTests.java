package com.example.user_service;

import com.example.events.EmailVerificationEvent;
import com.example.events.EmailVerificationSuccessfulEvent;
import com.example.user_service.auth.AuthService;
import com.example.user_service.exception.BadRequest;
import com.example.user_service.exception.NotFound;
import com.example.user_service.userAccount.UserAccount;
import com.example.user_service.userAccount.UserAccountRepo;
import com.example.user_service.verificationToken.ResendVerificationRequest;
import com.example.user_service.verificationToken.VerificationToken;
import com.example.user_service.verificationToken.VerificationTokenRepo;
import com.example.user_service.verificationToken.VerificationTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;


import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ResendEmailVerificationTests {

    @InjectMocks
    private AuthService authService;

    @Mock
    private VerificationTokenRepo verificationTokenRepo;

    @Mock
    private UserAccountRepo userAccountRepo;

    @Mock
    private KafkaTemplate<String, EmailVerificationEvent> kafkaTemplate;

    @Test
    void shouldResendVerificationEmailSuccessfully() {

        UserAccount user = UserAccount.builder()
                .email("john@gmail.com")
                .verified(false)
                .build();

        ResendVerificationRequest request =
                new ResendVerificationRequest("john@gmail.com");

        when(userAccountRepo.findByEmailIgnoreCase(request.email()))
                .thenReturn(Optional.of(user));

        authService.resendVerificationEmail(request);

        verify(verificationTokenRepo).deleteByUserAccount(user);
        verify(verificationTokenRepo).save(any(VerificationToken.class));

        verify(kafkaTemplate).send(
                eq("email.verification"),
                eq(user.getEmail()),
                any(EmailVerificationEvent.class)
        );
    }

    @Test
    void shouldThrowWhenUserNotFound() {

        ResendVerificationRequest request =
                new ResendVerificationRequest("john@gmail.com");

        when(userAccountRepo.findByEmailIgnoreCase(request.email()))
                .thenReturn(Optional.empty());

        NotFound exception = assertThrows(
                NotFound.class,
                () -> authService.resendVerificationEmail(request)
        );

        assertEquals("User not found", exception.getMessage());

        verify(verificationTokenRepo, never()).deleteByUserAccount(any());
        verify(verificationTokenRepo, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void shouldThrowWhenEmailAlreadyVerified() {

        UserAccount user = UserAccount.builder()
                .email("john@gmail.com")
                .verified(true)
                .build();

        ResendVerificationRequest request =
                new ResendVerificationRequest("john@gmail.com");

        when(userAccountRepo.findByEmailIgnoreCase(request.email()))
                .thenReturn(Optional.of(user));

        BadRequest exception = assertThrows(
                BadRequest.class,
                () -> authService.resendVerificationEmail(request)
        );

        assertEquals("Email already verified", exception.getMessage());

        verify(verificationTokenRepo, never()).deleteByUserAccount(any());
        verify(verificationTokenRepo, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }
}
