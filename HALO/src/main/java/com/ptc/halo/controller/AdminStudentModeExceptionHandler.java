package com.ptc.halo.controller;

import com.ptc.halo.service.AdminApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.Set;

// Same Student validation/auth/error semantics, scoped only to the explicit acting routes.
@RestControllerAdvice(assignableTypes={AdminStudentModeController.class,AdminStudentPreviewController.class})
public class AdminStudentModeExceptionHandler extends StudentLessonExceptionHandler {
    private static final Set<String> SAFE_CODES=Set.of(
        "PREVIEW_LIMIT_REACHED","PREVIEW_STATE_CHANGED","PREVIEW_OPERATION_PENDING","RESOURCE_NOT_FOUND","MODULE_NOT_FOUND","SESSION_NOT_FOUND","SESSION_NOT_OWNED","EXCHANGE_NOT_FOUND",
        "AUTHENTICATION_REQUIRED","INVALID_ROLE","ACCOUNT_INACTIVE","STUDENT_NOT_ENROLLED",
        "LESSON_NOT_APPROVED","LESSON_NOT_GENERATED","WEEK_LOCKED","SUBJECT_NOT_FOUND","WEEK_NOT_FOUND","MODULE_WEEK_NOT_FOUND","PREVIOUS_WEEK_INCOMPLETE",
        "ASSESSMENT_NOT_FOUND","ASSESSMENT_NOT_AVAILABLE","ASSESSMENT_ALREADY_PASSED","ASSESSMENT_ALREADY_SUBMITTED",
        "ATTEMPT_NOT_FOUND","ATTEMPT_NOT_SUBMITTED","ASSESSMENT_NOT_SUBMITTED","ACCESS_DENIED","INVALID_ANSWER_SET","ANSWERS_ALREADY_RECORDED",
        "MENTOR_STATE_CHANGED","MESSAGE_REQUIRED","INVALID_REQUEST_ID","REQUEST_ID_REUSED","VALIDATION_ERROR",
        "MODULE_INDEX_UNAVAILABLE","MODULE_MATERIALS_REQUIRED","MODULE_MATERIAL_MISMATCH","MENTOR_EMPTY_RESPONSE","MENTOR_PROVIDER_UNAVAILABLE");
    @Override
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,Object>> status(ResponseStatusException error,HttpServletRequest request) {
        String reason=error.getReason();
        String code=reason!=null && SAFE_CODES.contains(reason)?reason:"REQUEST_FAILED";
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("status",error.getStatusCode().value(),"code",code,"message",code));
    }
    @ExceptionHandler(AdminApiException.class)
    public ResponseEntity<Map<String,Object>> acting(AdminApiException error) {
        return ResponseEntity.status(error.code.status).body(Map.of("status",error.code.status,"code",error.code.name(),"message",error.code.message));
    }
    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    public ResponseEntity<Map<String,Object>> missingHeader() {
        return ResponseEntity.badRequest().body(Map.of("status",400,"code","INVALID_REQUEST","message","X-Acting-Session is required."));
    }
}
