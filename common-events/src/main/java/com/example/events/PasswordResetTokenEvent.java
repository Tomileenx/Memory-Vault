package com.example.events;

public record PasswordResetTokenEvent(
        String email,
        String token
) {
}
