package com.example.notification_service.service;

import com.example.notification_service.config.BrevoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final WebClient webClient;

    private final BrevoProperties brevoProperties;

    public void sendEmail(String to, String subject, String body) {
        Objects.requireNonNull(to, "Recipient email cannot be null");
        Objects.requireNonNull(brevoProperties.getSenderEmail(), "Sender email cannot be null");
        Objects.requireNonNull(brevoProperties.getSenderName(), "Sender name cannot be null");

        try {
            webClient.post()
                    .header("api-key", brevoProperties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "sender", Map.of(
                                    "name", brevoProperties.getSenderName(),
                                    "email", brevoProperties.getSenderEmail()
                            ),
                            "to", List.of(Map.of("email", to)),
                            "subject", subject,
                            "textContent", body
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("Email sent successfully to {}", to);
        } catch (WebClientResponseException e) {
            log.error(
                    "Brevo returned status {}",
                    e.getStatusCode(),
                    e
            );

            throw new RuntimeException("Unable to send email");
        } catch (Exception e) {
            log.error("Unexpected email error", e);

            throw new RuntimeException("Unable to send email");
        }
    }

    public void sendVerificationEmail(String email, String verificationLink) {
        String subject = "Email Verification";

        String body =
                """
                Welcome to memoryVault.

                Click the link below to verify your email:

                %s

                This link expires in 24 hours.
                """.formatted(verificationLink);

        sendEmail(email, subject, body);
    }

    public void sendAccountDetails(
            String email,
            String fullname,
            String username
    ) {
        String subject = "Email Verification Successful!";

        String body = """
                      Your Account is now active.
                     \s
                      Wallet information:
                      Full Name: %s
                      Username: %s
                     \s
                      You can now:
                      Store videos
                      Stream videos
                     \s
                      Welcome aboard!\s
                     \s
                      memoryVault Team
                     \s""".formatted(fullname, username);

        sendEmail(email, subject, body);
    }

    public void sendPasswordResetToken(
            String email,
            String passwordResetLink
    ) {
        String subject = "Password Reset";

        String body =
                """
                        Click the link below to reset your password:
                        
                        %s
                        
                        This link expires in 15 minutes.
                        """.formatted(passwordResetLink);

        sendEmail(email, subject, body);
    }
}
