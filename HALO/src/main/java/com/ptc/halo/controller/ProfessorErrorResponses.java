package com.ptc.halo.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

public class ProfessorErrorResponses {
    private static final Map<String, String> MESSAGES = Map.ofEntries(
        Map.entry("AUTHENTICATION_REQUIRED", "Please sign in to continue."),
        Map.entry("ACCESS_DENIED", "You do not have permission to access this resource."),
        Map.entry("RESOURCE_NOT_FOUND", "The requested resource is unavailable."),
        Map.entry("INVALID_REQUEST", "Check the request fields and try again."),
        Map.entry("INVALID_PAGINATION", "Page must be non-negative and size must be positive."),
        Map.entry("RESOURCE_CONFLICT", "The change conflicts with existing data. Reload before trying again."),
        Map.entry("SUBJECT_CODE_ALREADY_EXISTS", "This subject code is already used. Choose another code."),
        Map.entry("SUBJECT_NAME_ALREADY_EXISTS", "This subject name is already used. Choose another name."),
        Map.entry("SUBJECT_HAS_CONTENT", "This subject still contains weeks or learning modules. Remove its content before deleting the subject."),
        Map.entry("WEEK_NUMBER_ALREADY_EXISTS", "This subject already has that week number. Choose another week number."),
        Map.entry("STALE_MODULE_OPERATION", "This module changed. Reload it before trying again."),
        Map.entry("MODULE_ALREADY_APPROVED", "This lesson is already approved. Reload to see its current status."),
        Map.entry("MODULE_GENERATION_REQUIRED", "Generate the lesson successfully before this action."),
        Map.entry("MODULE_NOT_EDITABLE", "Published lessons cannot be changed through the draft editor."),
        Map.entry("MODULE_MATERIALS_REQUIRED", "Upload at least one original lesson file before publishing."),
        Map.entry("MODULE_MATERIAL_UNREADABLE", "Replace the unreadable original lesson file and try again."),
        Map.entry("MODULE_MATERIAL_UNSUPPORTED", "Replace unsupported original lesson files before publishing."),
        Map.entry("MODULE_MATERIAL_MISMATCH", "Reload and check this module's attachments before publishing."),
        Map.entry("MODULE_MATERIALS_TOO_LARGE", "Reduce the original lesson material size before publishing."),
        Map.entry("MODULE_INDEX_UNAVAILABLE", "Lesson material preparation is unavailable. Try again later."),
        Map.entry("UPLOAD_FILE_UNSUPPORTED", "Only readable PDF, PNG, and JPEG files are supported."),
        Map.entry("UPLOAD_FILE_UNREADABLE", "The file is empty, corrupt, protected, or unreadable. Choose a readable file."),
        Map.entry("UPLOAD_FILE_TOO_LARGE", "Each lesson file must be no larger than 3 MB."),
        Map.entry("UPLOAD_FILENAME_REQUIRED", "A filename is required."),
        Map.entry("MODULE_FILE_LIMIT_EXCEEDED", "A module can contain at most 10 lesson files."),
        Map.entry("PROFESSOR_REQUEST_FAILED", "The request could not be completed. Please try again later.")
    );
    public static ResponseEntity<Map<String, Object>> reply(int status, String code) {
        return ResponseEntity.status(status).body(Map.of("status", status, "code", code, "message", MESSAGES.get(code)));
    }
    public static ResponseEntity<Map<String, Object>> status(ResponseStatusException error) {
        int status = error.getStatusCode().value();
        // Preserve the publication conflict contract for materials that cannot be prepared.
        if (java.util.Set.of("MODULE_MATERIAL_UNREADABLE", "MODULE_MATERIAL_UNSUPPORTED", "MODULE_MATERIAL_MISMATCH", "MODULE_MATERIALS_TOO_LARGE")
                .contains(error.getReason() == null ? "" : error.getReason()) && status != 401 && status != 403) status = 409;
        String code = switch (status) {
            case 401 -> "AUTHENTICATION_REQUIRED";
            case 403 -> "ACCESS_DENIED";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 409 -> "RESOURCE_CONFLICT";
            default -> status >= 500 ? "PROFESSOR_REQUEST_FAILED" : "INVALID_REQUEST";
        };
        if (status != 401 && status != 403 && status != 404 && MESSAGES.containsKey(error.getReason() == null ? "" : error.getReason())) code = error.getReason();
        return reply(status, code);
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> business(ResponseStatusException error) { return status(error); }
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<?> authentication() { return reply(401, "AUTHENTICATION_REQUIRED"); }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<?> forbidden() { return reply(403, "ACCESS_DENIED"); }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<?> conflict() { return reply(409, "RESOURCE_CONFLICT"); }
    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<?> stale() { return reply(409, "STALE_MODULE_OPERATION"); }
    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<?> missingInput() { return reply(400, "INVALID_REQUEST"); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception error) {
        // Deliberately omit exception messages, stack traces and request details.
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Professor request failed; exceptionType={}", error.getClass().getSimpleName());
        return reply(500, "PROFESSOR_REQUEST_FAILED");
    }
    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public ResponseEntity<?> multipart(org.springframework.web.multipart.MultipartException error) {
        return new ApiMethodExceptionHandler().multipart(error);
    }
}
