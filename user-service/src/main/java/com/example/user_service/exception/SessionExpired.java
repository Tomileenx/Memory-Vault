package com.example.user_service.exception;

public class SessionExpired extends RuntimeException {
    public SessionExpired(String message) {
        super(message);
    }
}
