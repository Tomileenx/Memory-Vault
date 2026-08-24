package com.example.user_service.exception;

public class InvalidPasswordReset extends RuntimeException {
    public InvalidPasswordReset(String message) {
        super(message);
    }
}
