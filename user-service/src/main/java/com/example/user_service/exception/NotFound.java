package com.example.user_service.exception;

public class NotFound extends RuntimeException {
    public NotFound(String message) {
        super(message);
    }
}
