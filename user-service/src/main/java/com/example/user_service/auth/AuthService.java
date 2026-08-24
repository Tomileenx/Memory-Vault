package com.example.user_service.auth;

import com.example.events.EmailVerificationEvent;
import com.example.user_service.config.JWTService;
import com.example.user_service.config.UserPrincipal;
import com.example.user_service.exception.AlreadyExists;
import com.example.user_service.exception.BadRequest;
import com.example.user_service.exception.NotFound;
import com.example.user_service.passwordResetToken.ForgotPasswordRequest;
import com.example.user_service.refreshToken.RefreshTokenResponse;
import com.example.user_service.refreshToken.RefreshTokenService;
import com.example.user_service.userAccount.UserAccount;
import com.example.user_service.userAccount.UserAccountRepo;
import com.example.user_service.verificationToken.ResendVerificationRequest;
import com.example.user_service.verificationToken.VerificationToken;
import com.example.user_service.verificationToken.VerificationTokenRepo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@Getter
@RequiredArgsConstructor
public class AuthService {
    private final UserAccountRepo userAccountRepo;
    private final JWTService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final VerificationTokenRepo verificationTokenRepo;
    private final KafkaTemplate<String, EmailVerificationEvent> kafkaTemplate;

    private static final String EMAIL_VERIFICATION_TOPIC = "email.verification";

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        log.info("Registering user email: {}", request.email());

        if (userAccountRepo.existsByEmailIgnoreCase(request.email())) {
            throw new AlreadyExists(request.email() + " already exist");
        }

        if (userAccountRepo.existsByUsernameIgnoreCase(request.username())) {
            throw new AlreadyExists(request.username() + " already taken");
        }

        String passwordHash = passwordEncoder.encode(request.password());

        UserAccount user = UserAccount.builder()
                .fullName(request.fullName())
                .username(request.username())
                .email(request.email())
                .password(passwordHash)
                .verified(false)
                .createdAt(Instant.now())
                .build();

        userAccountRepo.save(user);

        log.info("User registration successful: {}", user.getEmail());

        String verificationToken =
                UUID.randomUUID().toString();

        VerificationToken token = VerificationToken.builder()
                .userAccount(user)
                .token(verificationToken)
                .expiresAt(Instant.now().plus(Duration.ofHours(24)))
                .build();

        verificationTokenRepo.save(token);

        EmailVerificationEvent event = new EmailVerificationEvent(
                user.getEmail(),
                verificationToken
        );

        kafkaTemplate.send(EMAIL_VERIFICATION_TOPIC, user.getEmail(), event);

        log.info("Email verification event published for email: {}", user.getEmail());

        return new RegisterResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPassword(),
                user.getCreatedAt(),
                "Email verification link sent to your email"
        );
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication =
                authManager.authenticate(new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                ));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UserAccount user = principal.getUser();

        if (!user.isVerified()) {
            throw new BadRequest("Please verify your email first");
        }

        String token = jwtService.generateToken(principal);
        RefreshTokenResponse refresh = refreshTokenService.createRefreshToken(user);

        return new LoginResponse(
                user.getFullName(),
                user.getUsername(),
                token,
                refresh.refreshToken()
        );
    }

    @Transactional
    public void resendVerificationEmail(
            ResendVerificationRequest request
    ) {

        UserAccount user = userAccountRepo
                .findByEmailIgnoreCase(request.email())
                .orElseThrow(
                        () -> new NotFound(
                                "User not found"
                        )
                );

        if (user.isVerified()) {
            throw new BadRequest(
                    "Email already verified"
            );
        }

        verificationTokenRepo
                .deleteByUserAccount(user);

        String verificationToken =
                UUID.randomUUID().toString();

        VerificationToken token = VerificationToken.builder()
                .token(verificationToken)
                .userAccount(user)
                .expiresAt(Instant.now().plus(Duration.ofHours(24)))
                .build();

        verificationTokenRepo.save(token);

        EmailVerificationEvent event = new EmailVerificationEvent(
                user.getEmail(),
                verificationToken
        );

        kafkaTemplate.send(EMAIL_VERIFICATION_TOPIC, user.getEmail(), event);

        log.info("Email resend verification event published for email: {}", user.getEmail());
    }
}
