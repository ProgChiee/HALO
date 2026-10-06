package com.ptc.halo.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.ResponseEntity;
import java.util.Map;
// Only Bean Validation errors; authentication and password-reset handling are unchanged.
@RestControllerAdvice(assignableTypes = AuthController.class)
public class PasswordChangeValidationHandler {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<?> passwordError(org.springframework.web.server.ResponseStatusException error) {
        if (error.getStatusCode().value() == 400) {
            String code = error.getReason();
            if ("INCORRECT_CURRENT_PASSWORD".equals(code) || "INVALID_NEW_PASSWORD".equals(code))
                return ResponseEntity.badRequest().body(Map.of("status", 400, "code", code, "message",
                    "INCORRECT_CURRENT_PASSWORD".equals(code) ? "Your current password is incorrect. Try again." : "Choose a different password with at least 8 characters."));
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
