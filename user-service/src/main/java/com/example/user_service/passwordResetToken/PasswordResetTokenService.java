package com.example.user_service.passwordResetToken;

import com.example.events.EmailVerificationSuccessfulEvent;
import com.example.events.PasswordResetTokenEvent;
import com.example.user_service.exception.BadRequest;
import com.example.user_service.exception.NotFound;
import com.example.user_service.exception.Unauthorized;
import com.example.user_service.userAccount.UserAccount;
import com.example.user_service.userAccount.UserAccountRepo;
import com.example.user_service.verificationToken.VerificationToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetTokenService {
    private final UserAccountRepo userAccountRepo;
    private final PasswordResetTokenRepo passwordResetTokenRepo;
    private final PasswordEncoder passwordEncoder;

    private final KafkaTemplate<String, PasswordResetTokenEvent> kafkaTemplate;

    private static final String PASSWORD_RESET_TOKEN_TOPIC = "password.reset.token";

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        UserAccount user = userAccountRepo.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new NotFound(request.email() + " not found"));

        // Delete all tokens for a specific user
        passwordResetTokenRepo.deleteAllByUserAccount(user);

        String token = UUID.randomUUID().toString();

        PasswordResetToken passwordResetToken = PasswordResetToken.builder()
                .token(token)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
                .userAccount(user)
                .build();

        passwordResetTokenRepo.save(passwordResetToken);

        PasswordResetTokenEvent event = new PasswordResetTokenEvent(
                user.getEmail(),
                token
        );

        kafkaTemplate.send(PASSWORD_RESET_TOKEN_TOPIC, user.getEmail(), event);

        log.info("Password reset token event published for email: {}", user.getEmail());
    }

    @Transactional
    public void resetPassword(String token, ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepo.findByToken(token)
                .orElseThrow(() -> new Unauthorized("Invalid or expired token"));

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new Unauthorized("Token Expired");
        }

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequest("Passwords do not match");
        }

        UserAccount user = resetToken.getUserAccount();
        user.setPassword(passwordEncoder.encode(request.newPassword()));

        passwordResetTokenRepo.delete(resetToken);

        log.info("Password reset successful for {}", user.getEmail());
    }
}
