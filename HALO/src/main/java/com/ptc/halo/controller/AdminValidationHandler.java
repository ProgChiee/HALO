package com.ptc.halo.controller;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice(assignableTypes = {AdminController.class, AdminDashboardController.class,
        AdminProfessorMonitoringController.class, AdminStudentMonitoringController.class})
public class AdminValidationHandler {
    private ResponseEntity<?> response(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status, "code", code, "message", message));
    }
    @ExceptionHandler(com.ptc.halo.service.AdminApiException.class)
    public ResponseEntity<?> business(com.ptc.halo.service.AdminApiException error) {
        return response(error.code.status, error.code.name(), error.code.message);
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<?> integrity(org.springframework.dao.DataIntegrityViolationException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().toLowerCase(java.util.Locale.ROOT).contains("uk_user_email_normalized"))
                return business(new com.ptc.halo.service.AdminApiException(com.ptc.halo.service.AdminApiException.Code.EMAIL_ALREADY_EXISTS));
        }
        return unexpected(error);
    }
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<?> authentication() { return response(401, "AUTHENTICATION_REQUIRED", "Please sign in to continue."); }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<?> forbidden() { return response(403, "ACCESS_DENIED", "You do not have permission to perform this action."); }
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<?> missingParameter() { return invalid(Map.of("request", "A required parameter is missing.")); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception error) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("admin_request_failed exceptionType={}", error.getClass().getSimpleName());
        return response(500, "ADMIN_REQUEST_FAILED", "Unable to complete the request. Please try again later.");
    }
    private ResponseEntity<?> invalid(Map<String, String> fields) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "code", "VALIDATION_FAILED",
                "message", "Please correct the invalid fields.", "errors", fields));
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
                fields.put(result.getMethodParameter().getParameterName(), "must be a positive integer");
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
