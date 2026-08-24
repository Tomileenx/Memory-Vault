package com.example.user_service.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email
        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "username is required")
        String username,

        @Size(min = 8, message = "Password must at least eight characters")
        @NotBlank(message = "Password is required")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$#!%*?&])[A-Za-z\\d@$#!%*?&]{8,}$",
                message = "Password must be at least 8 characters and include uppercase, lowercase, number, and special character"
        )
        String password
) {
}
