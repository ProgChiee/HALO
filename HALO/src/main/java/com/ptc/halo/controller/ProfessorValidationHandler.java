package com.ptc.halo.controller;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

// Validation only: business errors and security handling remain unchanged.
@RestControllerAdvice(assignableTypes = {ProfessorAcademicController.class, AiLearningModuleController.class})
public class ProfessorValidationHandler {
    private ResponseEntity<?> invalid(Map<String, String> fields) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "code", "VALIDATION_FAILED",
                "message", "Please correct the invalid fields.", "errors", fields));
    }

    @ExceptionHandler(ProfessorInputException.class)
    public ResponseEntity<?> sources(ProfessorInputException error) {
        return invalid(Map.of(error.field, error.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> body(MethodArgumentNotValidException error) {
        Map<String, String> fields = new TreeMap<>();
        error.getBindingResult().getFieldErrors().forEach(field ->
                fields.merge(field.getField(), field.getDefaultMessage(), (a, b) -> a.compareTo(b) <= 0 ? a : b));
        return invalid(fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<?> parameters(HandlerMethodValidationException error) {
        Map<String, String> fields = new TreeMap<>();
        error.getAllValidationResults().forEach(result -> {
            if (result instanceof org.springframework.validation.method.ParameterErrors bean) {
                bean.getFieldErrors().forEach(field -> fields.merge(field.getField(),
                        field.getDefaultMessage(), (a, b) -> a.compareTo(b) <= 0 ? a : b));
            } else {
                fields.put(result.getMethodParameter().getParameterName(), "Invalid value or length.");
            }
        });
        return invalid(fields);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> type(MethodArgumentTypeMismatchException error) {
        return invalid(Map.of(error.getName(), "Invalid value."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> unreadable(HttpMessageNotReadableException error) {
        return invalid(Map.of("request", "Invalid JSON or field value. Use an existing enum value."));
    }
}
