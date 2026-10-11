package com.ptc.halo.service;
public class PasswordResetException extends RuntimeException {
    private final int status;
    public PasswordResetException(int status) { super("Password reset request failed"); this.status=status; }
    public int getStatus() { return status; }
}
