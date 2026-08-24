package com.example.user_service.refreshToken;

import com.example.user_service.config.JWTService;
import com.example.user_service.config.UserPrincipal;
import com.example.user_service.exception.SessionExpired;
import com.example.user_service.exception.TokenExpired;
import com.example.user_service.exception.Unauthorized;
import com.example.user_service.userAccount.UserAccount;
import com.example.user_service.userAccount.UserAccountRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepo refreshTokenRepo;
    private final UserAccountRepo userRepo;
    private final JWTService jwtService;

    private final long REFRESH_EXPIRATION = 24 * 60 * 60 * 1000;
    private final long IN_ACTIVITY_LIMIT =  15 * 60 * 1000;

    @Transactional
    public RefreshTokenResponse createRefreshToken(UserAccount user) {

        Instant now = Instant.now();

        String refreshToken = UUID.randomUUID().toString();

        RefreshToken token = RefreshToken.builder()
                .tokenHash(refreshToken)
                .userAccount(user)
                .expiryDate(now.plusMillis(REFRESH_EXPIRATION))
                .lastActivity(now)
                .build();

        RefreshToken saved = refreshTokenRepo.save(token);

        return new RefreshTokenResponse(
                refreshToken,
                saved.getExpiryDate()
        );
    }

    @Transactional
    public VerifyRefreshTokenResponse verifyToken(RefreshTokenRequest request) {
        RefreshToken rToken = refreshTokenRepo.findByTokenHash(request.refreshToken())
                .orElseThrow(() -> new Unauthorized("Invalid Refresh Token"));

        if (rToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepo.delete(rToken);
            throw new TokenExpired("Refresh token expired");
        }

        if (rToken.getLastActivity().plusMillis(IN_ACTIVITY_LIMIT).isBefore(Instant.now())) {
            refreshTokenRepo.delete(rToken);
            throw new SessionExpired("Session expired due to inactivity");
        }

        String newAccessToken =
                jwtService.generateToken(
                        new UserPrincipal(rToken.getUserAccount())
                );

        rToken.setLastActivity(Instant.now());
        refreshTokenRepo.save(rToken);

        return new VerifyRefreshTokenResponse(
                newAccessToken
        );
    }

    public void logout(String username) {
        UserAccount user = userRepo.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        refreshTokenRepo.deleteByUserAccount(user);
    }
}

