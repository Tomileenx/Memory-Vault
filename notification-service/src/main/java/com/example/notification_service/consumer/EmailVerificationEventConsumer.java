package com.example.notification_service.consumer;

import com.example.events.EmailVerificationEvent;
import com.example.notification_service.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;


@Service
@Slf4j
@RequiredArgsConstructor
public class EmailVerificationEventConsumer {

    private final EmailService emailService;

    @KafkaListener(
            topics = "email.verification",
            groupId = "email-service-group"
    )
    private void consumeEmailVerificationEvent(EmailVerificationEvent event) {
        log.info("Consumed EmailVerificationEvent for email: {}", event.email());

        try {
            String verificationLink =
                    "https://memoryVault/account/verification?token=" + event.token();

            emailService.sendVerificationEmail(event.email(), verificationLink);
        } catch (Exception e) {
            log.error("Failed to send email verification for email: {}", event.email());
        }
    }
}
