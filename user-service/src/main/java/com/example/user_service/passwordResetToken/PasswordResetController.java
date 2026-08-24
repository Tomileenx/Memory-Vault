package com.example.user_service.passwordResetToken;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/memVault")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetTokenService passwordResetTokenService;

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        passwordResetTokenService.forgotPassword(request);
        return ResponseEntity.ok(
                "Password reset token link has been sent to user's email"
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(
            @RequestParam String token,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        passwordResetTokenService.resetPassword(token, request);
        return ResponseEntity.ok(
                "Password successfully reset"
        );
    }
}
