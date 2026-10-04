package com.ptc.halo.controller;
import com.ptc.halo.service.SuperAdminApiException;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice(assignableTypes = SuperAdminController.class)
public class SuperAdminExceptionHandler {
    private ResponseEntity<Map<String,Object>> response(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status, "code", code, "message", message));
    }
    @ExceptionHandler(SuperAdminApiException.class)
    public ResponseEntity<Map<String,Object>> domain(SuperAdminApiException e) {
        return response(e.getStatus(), e.getCode(), e.getMessage());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> validation(MethodArgumentNotValidException e) {
        Map<String,String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("status",400,"code","VALIDATION_FAILED",
                "message","Please correct the invalid fields.","errors",fields));
    }
    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String,Object>> invalid(Exception e) {
        return response(400,"VALIDATION_FAILED","Invalid request fields or parameters.");
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,Object>> integrity(DataIntegrityViolationException e) {
        // Match only the known email index. Never send database text to the client.
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().toLowerCase(Locale.ROOT).contains("uk_user_email_normalized"))
                return domain(SuperAdminApiException.duplicateEmail());
        }
        return unexpected(e);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> unexpected(Exception e) throws RuntimeException {
        if (e instanceof AuthenticationException a) throw a;
        if (e instanceof AccessDeniedException a) throw a;
        org.slf4j.LoggerFactory.getLogger(getClass()).error("super_admin_request_failed exceptionType={}",e.getClass().getName());
        return response(500,"SUPER_ADMIN_REQUEST_FAILED","Unable to complete the request. Please try again.");
    }
}
