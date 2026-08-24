package com.example.notification_service.consumer;

import com.example.events.EmailVerificationSuccessfulEvent;
import com.example.notification_service.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailVerificationSuccessfulEventConsumer {

    private final EmailService emailService;

    @KafkaListener(
            topics = "email.verification.successful",
            groupId = "email-service-group"
    )
    private void consumeEmailVerificationSuccessfulEvent(EmailVerificationSuccessfulEvent event) {
        log.info("Consumed EmailVerificationSuccessfulEvent for email: {}", event.email());

        try {
            emailService.sendAccountDetails(event.email(), event.fullName(), event.username());
        } catch (Exception e) {
            log.error("Failed to send email verification successful for email: {}", event.email());
        }
    }
}
