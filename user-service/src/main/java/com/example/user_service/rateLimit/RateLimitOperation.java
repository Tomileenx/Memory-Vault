package com.example.user_service.rateLimit;

public enum RateLimitOperation {
    REGISTER,
    LOGIN,
    VERIFICATION,
    RESEND_VERIFICATION,
    FORGOT_PASSWORD,
    DEFAULT
}
