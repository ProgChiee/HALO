package com.ptc.halo.service;

/** Registration-specific presentation of the shared limiter's admission rejection. */
public class RegistrationRateLimitException extends PasswordResetException {
    public RegistrationRateLimitException(long retryAfterSeconds) {
        super(429, retryAfterSeconds);
    }
}
