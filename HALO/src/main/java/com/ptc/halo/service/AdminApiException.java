package com.ptc.halo.service;

public class AdminApiException extends RuntimeException {
    public enum Code {
        RESOURCE_NOT_FOUND(404, "The requested account or resource was not found."),
        INVALID_TARGET_ROLE(400, "This account does not have the required role for this action."),
        EMAIL_ALREADY_EXISTS(409, "An account with this email already exists.");
        public final int status;
        public final String message;
        Code(int status, String message) { this.status = status; this.message = message; }
    }
    public final Code code;
    public AdminApiException(Code code) { super(code.message); this.code = code; }
}
