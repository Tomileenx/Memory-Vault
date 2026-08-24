package com.example.user_service.refreshToken;

import com.example.user_service.userAccount.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepo extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String token);

    void deleteByUserAccount(UserAccount userAccount);
}
