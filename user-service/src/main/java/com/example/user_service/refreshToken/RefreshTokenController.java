package com.example.user_service.refreshToken;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/memVault")
@RequiredArgsConstructor
public class RefreshTokenController {
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/refreshToken")
    public ResponseEntity<VerifyRefreshTokenResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request
    ) {

        VerifyRefreshTokenResponse response =
                refreshTokenService.verifyToken(request);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @DeleteMapping("/logout")
    public ResponseEntity<Void> logout(@RequestParam String email) {
        refreshTokenService.logout(email);
        return ResponseEntity.noContent().build();
    }
}
