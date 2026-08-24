package com.example.user_service.verificationToken;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/memVault")
@RequiredArgsConstructor
public class VerificationTokenController {

    private final VerificationTokenService verificationTokenService;

    @GetMapping("/verify-email")
    public ResponseEntity<String> verify(
            @RequestParam String token
    ) {
        verificationTokenService.verifyEmail(token);
        return ResponseEntity.ok("Email verification successful");
    }
}
