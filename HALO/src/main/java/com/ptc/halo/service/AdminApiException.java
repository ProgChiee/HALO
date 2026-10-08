package com.ptc.halo.service;

public class AdminApiException extends RuntimeException {
    public enum Code {
        ACTING_INPUT_INVALID(400, "Please provide a valid target, role, and paging parameters."),
        ACTING_TARGET_INACTIVE(409, "Select an active account to start acting mode."),
        ACTING_SESSION_INVALID(409, "This acting session is no longer valid. Start a new acting session."),
        RESOURCE_NOT_FOUND(404, "The requested account or resource was not found."),
        INVALID_TARGET_ROLE(400, "This account does not have the required role for this action."),
        PROFESSOR_ID_CONFLICT(409, "This Professor ID is already in use. Enter a different Professor ID."),
        EMAIL_ALREADY_EXISTS(409, "An account with this email already exists.");
        public final int status;
        public final String message;
        Code(int status, String message) { this.status = status; this.message = message; }
    }
    public final Code code;
    public AdminApiException(Code code) { super(code.message); this.code = code; }
}
