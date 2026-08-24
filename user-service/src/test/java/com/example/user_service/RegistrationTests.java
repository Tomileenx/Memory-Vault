package com.example.user_service;

import com.example.events.EmailVerificationEvent;
import com.example.user_service.auth.AuthService;
import com.example.user_service.auth.RegisterRequest;
import com.example.user_service.auth.RegisterResponse;
import com.example.user_service.config.JWTService;
import com.example.user_service.exception.AlreadyExists;
import com.example.user_service.refreshToken.RefreshTokenService;
import com.example.user_service.userAccount.UserAccount;
import com.example.user_service.userAccount.UserAccountRepo;
import com.example.user_service.verificationToken.VerificationToken;
import com.example.user_service.verificationToken.VerificationTokenRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class RegistrationTests {

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

    @Mock
    private KafkaTemplate<String, EmailVerificationEvent> kafkaTemplate;

    @Test
    void shouldRegisterSuccessfully() {
        RegisterRequest request = new RegisterRequest(
                "JohnDoe@gmail.com",
                "John Doe",
                "JohnnyDoe",
                "passwordJon@1#"
        );

        when(userAccountRepo.existsByEmailIgnoreCase(request.email()))
                .thenReturn(false);

        when(userAccountRepo.existsByUsernameIgnoreCase(request.username()))
                .thenReturn(false);

        when(passwordEncoder.encode(request.password()))
                .thenReturn("hashed-password");

        when(userAccountRepo.save(any(UserAccount.class)))
                .thenAnswer(invocation -> {
                    UserAccount user = invocation.getArgument(0);
                    user.setId(UUID.randomUUID());
                    return user;
                });

        RegisterResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals(request.email(), response.email());

        verify(userAccountRepo).save(any(UserAccount.class));
        verify(verificationTokenRepo).save(any(VerificationToken.class));
        verify(kafkaTemplate).send(
                eq("email.verification"),
                eq(request.email()),
                any(EmailVerificationEvent.class)
        );
    }

    @Test
    void shouldThrowWhenEmailAlreadyExists() {

        RegisterRequest request = new RegisterRequest(
                "JohnDoe@gmail.com",
                "John Doe",
                "JohnnyDoe",
                "passwordJon@1#"
        );

        when(userAccountRepo.existsByEmailIgnoreCase(request.email()))
                .thenReturn(true);

        assertThrows(AlreadyExists.class,
                () -> authService.register(request));

        verify(userAccountRepo, never()).save(any());
        verify(verificationTokenRepo, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void shouldThrowWhenUsernameAlreadyExists() {
        RegisterRequest request = new RegisterRequest(
                "JohnDoe@gmail.com",
                "John Doe",
                "JohnnyDoe",
                "passwordJon@1#"
        );

        when(userAccountRepo.existsByUsernameIgnoreCase(request.username()))
                .thenReturn(true);

        assertThrows(AlreadyExists.class,
                () -> authService.register(request));

        verify(userAccountRepo, never()).save(any());
        verify(verificationTokenRepo, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }
}
