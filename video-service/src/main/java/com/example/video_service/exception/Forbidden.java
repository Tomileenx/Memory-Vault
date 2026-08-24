package com.example.video_service.exception;

public class Forbidden extends RuntimeException {
    public Forbidden(String message) {
        super(message);
    }
}
