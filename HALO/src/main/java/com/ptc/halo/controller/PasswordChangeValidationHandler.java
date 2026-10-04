package com.ptc.halo.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.ResponseEntity;
import java.util.Map;
// Only Bean Validation errors; authentication and password-reset handling are unchanged.
@RestControllerAdvice(assignableTypes = AuthController.class)
public class PasswordChangeValidationHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException error) {
        Map<String,String> fields = new java.util.LinkedHashMap<>();
        error.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("status",400,"code","VALIDATION_FAILED",
            "message","Please correct the invalid fields.","errors",fields));
    }
}
