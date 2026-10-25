package com.ptc.halo.service;
public class PasswordResetException extends RuntimeException {
    private final int status;
    private final long retryAfterSeconds;
    public PasswordResetException(int status) { this(status,0); }
    public PasswordResetException(int status,long retryAfterSeconds) { super("Password reset request failed"); this.status=status; this.retryAfterSeconds=retryAfterSeconds; }
    public int getStatus() { return status; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
