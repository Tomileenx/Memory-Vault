package com.example.user_service.verificationToken;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class VerificationTokenCleanupJob {
    private final VerificationTokenRepo verificationTokenRepo;

    @Scheduled(cron = "0 0 * * * *")
    public void cleanUpExpiredTokens() {
        verificationTokenRepo.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}
