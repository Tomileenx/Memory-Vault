package com.example.user_service.passwordResetToken;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @Email
        @NotBlank(message = "Email is required")
        String email
) {
}
