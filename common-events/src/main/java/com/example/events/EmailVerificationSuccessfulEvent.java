package com.example.events;

public record EmailVerificationSuccessfulEvent(
        String email,
        String fullName,
        String username
) {
}
