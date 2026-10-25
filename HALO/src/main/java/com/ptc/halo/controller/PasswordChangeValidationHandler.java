package com.ptc.halo.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.ResponseEntity;
import java.util.Map;
// Safe authentication input and password errors; protected-route rules are unchanged.
@RestControllerAdvice(assignableTypes = AuthController.class)
public class PasswordChangeValidationHandler {
    @ExceptionHandler(com.ptc.halo.service.RegistrationValidationException.class)
    public ResponseEntity<?> registration(com.ptc.halo.service.RegistrationValidationException error) {
        return ResponseEntity.status(error.status).body(Map.of("status",error.status,"code","REGISTRATION_VALIDATION_FAILED","message","Please correct the indicated fields.","errors",error.errors));
    }


    @ExceptionHandler(com.ptc.halo.service.PasswordResetException.class)
    public ResponseEntity<?> resetError(com.ptc.halo.service.PasswordResetException error) {
        int status=error.getStatus();
        if(status==429) return ResponseEntity.status(429).header("Retry-After",Long.toString(error.getRetryAfterSeconds()))
                .body(Map.of("status",429,"code","PASSWORD_RESET_RATE_LIMITED","retryAfterSeconds",error.getRetryAfterSeconds(),
                        "message","Too many recovery attempts. Please wait before trying again."));
        return ResponseEntity.status(status).body(Map.of("status",status,"code",status==400?"INVALID_RESET_REQUEST":"PASSWORD_RESET_FAILED",
            "message",status==400?"The code could not be verified. Check your email and code. If it has expired or reached its attempt limit, wait before requesting a new code.":"Password reset is unavailable. Please try again later."));
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<?> malformed(org.springframework.http.converter.HttpMessageNotReadableException error) {
        if (error.getMostSpecificCause() instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException invalid
                && invalid.getTargetType() == com.ptc.halo.enums.YearLevel.class)
            return ResponseEntity.badRequest().body(Map.of("status",400,"code","REGISTRATION_VALIDATION_FAILED","message","Select a valid year level.","errors",Map.of("yearLevel","Select your year level.")));
        return ResponseEntity.badRequest().body(Map.of("status",400,"code","INVALID_REQUEST","message","Check the request format and try again."));
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<?> passwordError(org.springframework.web.server.ResponseStatusException error) {
        if (error.getStatusCode().value() == 400) {
            String code = error.getReason();
            if ("INCORRECT_CURRENT_PASSWORD".equals(code) || "INVALID_NEW_PASSWORD".equals(code))
                return ResponseEntity.badRequest().body(Map.of("status", 400, "code", code, "message",
                    "INCORRECT_CURRENT_PASSWORD".equals(code) ? "Your current password is incorrect. Try again." : "Choose a different password with at least 8 characters and at most 72 UTF-8 bytes."));
        }
        return ProfessorErrorResponses.status(error);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException error) {
        Map<String,String> fields = new java.util.LinkedHashMap<>();
        error.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("status",400,"code","VALIDATION_FAILED",
            "message","Please correct the invalid fields.","errors",fields));
    }
}
