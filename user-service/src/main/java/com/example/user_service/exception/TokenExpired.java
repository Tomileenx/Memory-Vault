package com.example.user_service.exception;

public class TokenExpired extends RuntimeException {
    public TokenExpired(String message) {
        super(message);
    }
}
