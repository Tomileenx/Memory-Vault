package com.example.user_service.verificationToken;

import com.example.user_service.userAccount.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepo extends JpaRepository<VerificationToken, UUID> {
    Optional<VerificationToken> findByToken(String token);

    void deleteByUserAccount(
            UserAccount user
    );

    void deleteByExpiresAtBefore(LocalDateTime time);
}
