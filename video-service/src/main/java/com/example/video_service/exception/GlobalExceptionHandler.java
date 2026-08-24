package com.example.video_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AlreadyExists.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(
            AlreadyExists ex
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        ex.getMessage(),
                        "ALREADY_EXISTS",
                        Instant.now()
                ));

    }

    @ExceptionHandler(NotFound.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            NotFound ex
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        ex.getMessage(),
                        "NOT_FOUND",
                        Instant.now()
                ));
    }

    @ExceptionHandler(Unauthorized.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            Unauthorized ex
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(
                        ex.getMessage(),
                        "UNAUTHORIZED",
                        Instant.now()
                ));
    }

    @ExceptionHandler(Forbidden.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
            HttpClientErrorException.Forbidden ex
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(
                        ex.getMessage(),
                        "FORBIDDEN",
                        Instant.now()
                ));
    }

    @ExceptionHandler(BadRequest.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            BadRequest ex
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        ex.getMessage(),
                        "BAD_REQUEST",
                        Instant.now()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgument(
            MethodArgumentNotValidException ex
    ) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("Validation Failed");

        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(
                        message,
                        "VALIDATION_ERROR",
                        Instant.now()
                ));
    }

    @ExceptionHandler({
            BadCredentialsException.class,
            UsernameNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleAuthExceptions(
            RuntimeException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(
                        ex.getMessage(),
                        "INVALID_CREDENTIALS",
                        Instant.now()
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFormat(
            HttpMessageNotReadableException ex
    ) {
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(
                        "Invalid request format",
                        "INVALID_REQUEST_FORMAT",
                        Instant.now()
                ));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(TooManyRequestsException ex) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(
                        ex.getMessage(),
                        "TOO_MANY_REQUESTS",
                        Instant.now()
                ));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleGenerics(
            Exception ex
    ) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        ex.getMessage(),
                        "INTERNAL_ERROR",
                        Instant.now()
                ));
    }
}
