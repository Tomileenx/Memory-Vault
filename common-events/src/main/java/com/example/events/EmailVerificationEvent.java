package com.example.events;

public record EmailVerificationEvent(
        String email,
        String token
) {
}
