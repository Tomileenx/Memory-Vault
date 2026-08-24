package com.example.user_service.verificationToken;

import com.example.events.EmailVerificationEvent;
import com.example.events.EmailVerificationSuccessfulEvent;
import com.example.user_service.auth.AuthService;
import com.example.user_service.exception.BadRequest;
import com.example.user_service.exception.NotFound;
import com.example.user_service.userAccount.UserAccount;
import com.example.user_service.userAccount.UserAccountRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationTokenService {

    private final VerificationTokenRepo verificationTokenRepo;
    private final UserAccountRepo userAccountRepo;

    private final KafkaTemplate<String, EmailVerificationSuccessfulEvent> kafkaTemplate;

    private static final String EMAIL_VERIFICATION_SUCCESSFUL_TOPIC = "email.verification.successful";


    @Transactional
    public void verifyEmail(String tokenValue) {

        VerificationToken token = verificationTokenRepo.findByToken(tokenValue)
                .orElseThrow(() -> new NotFound("Invalid token"));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequest("Verification token expired");
        }

        UserAccount user = token.getUserAccount();

        if (user.isVerified()) {
            throw new BadRequest("Email already verified");
        }

        user.setVerified(true);

        userAccountRepo.save(user);

        verificationTokenRepo.delete(token);

        EmailVerificationSuccessfulEvent event = new EmailVerificationSuccessfulEvent(
                user.getEmail(),
                user.getFullName(),
                user.getUsername()
        );

        kafkaTemplate.send(EMAIL_VERIFICATION_SUCCESSFUL_TOPIC, user.getEmail(), event);

        log.info("Email verification event published for email: {}", user.getEmail());
    }
}
