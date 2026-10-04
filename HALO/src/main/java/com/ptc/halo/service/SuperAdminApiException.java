package com.ptc.halo.service;
public class SuperAdminApiException extends RuntimeException {
    private final int status;
    private final String code;
    public SuperAdminApiException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public int getStatus() { return status; }
    public String getCode() { return code; }
    public static SuperAdminApiException duplicateEmail() {
        return new SuperAdminApiException(409, "EMAIL_ALREADY_EXISTS", "An account with this email already exists.");
    }
    public static SuperAdminApiException notFound() {
        return new SuperAdminApiException(404, "ADMIN_NOT_FOUND", "Admin account not found.");
    }
}
