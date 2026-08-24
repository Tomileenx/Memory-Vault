package com.example.notification_service.consumer;

import com.example.events.EmailVerificationSuccessfulEvent;
import com.example.events.PasswordResetTokenEvent;
import com.example.notification_service.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PasswordResetTokenConsumer {

    private final EmailService emailService;

    @KafkaListener(
            topics = "password.reset.token",
            groupId = "email-service-group"
    )
    private void consumePasswordResetTokenEvent(PasswordResetTokenEvent event) {
        log.info("Consumed PasswordResetTokenEvent for email: {}", event.email());

        try {
            String passwordResetLink = "https://app.com/workflow/account/reset/password?token=" + event.token();

            emailService.sendPasswordResetToken(event.email(), passwordResetLink);
        } catch (Exception e) {
            log.error("Failed to send password reset token for email: {}", event.email());
        }
    }
}
