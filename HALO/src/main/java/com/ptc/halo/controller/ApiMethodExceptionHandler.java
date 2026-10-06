package com.ptc.halo.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Method mismatches happen before Spring can select a controller.
@RestControllerAdvice
public class ApiMethodExceptionHandler {
    // Multipart parsing can fail before a controller is selected.
    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public ResponseEntity<Map<String, Object>> multipart(org.springframework.web.multipart.MultipartException error) {
        boolean tooLarge = error instanceof org.springframework.web.multipart.MaxUploadSizeExceededException;
        int status = tooLarge ? 413 : 400;
        return ResponseEntity.status(status).body(Map.of("status", status,
                "code", tooLarge ? "UPLOAD_FILE_TOO_LARGE" : "INVALID_MULTIPART_REQUEST",
                "message", tooLarge ? "The upload exceeds the server size limit. Choose smaller files." : "The upload could not be parsed. Select the files again and retry."));
    }
    private static final Logger log = LoggerFactory.getLogger(ApiMethodExceptionHandler.class);
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> method(HttpRequestMethodNotSupportedException error, HttpServletRequest request) {
        log.warn("request_rejected method={} path={} status=405 reason=METHOD_NOT_ALLOWED",
                request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(405).header("Allow", String.join(", ", error.getSupportedMethods() == null ? new String[0] : error.getSupportedMethods()))
                .body(Map.of("status", 405, "code", "METHOD_NOT_ALLOWED", "message", "Use the supported HTTP method."));
    }
}
