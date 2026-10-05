package com.ptc.halo.controller;

import com.ptc.halo.dtoRequest.StudentAnswerRequest;
import com.ptc.halo.dtoResponse.*;
import com.ptc.halo.entity.AssessmentAttemptEntity;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.service.AssessmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/student/assessment")
public class StudentAssessmentController {

    private final AssessmentService assessmentService;
    private final UserRepository userRepository;

    public StudentAssessmentController(
            AssessmentService assessmentService, UserRepository userRepository) {

        this.assessmentService = assessmentService;
        this.userRepository = userRepository;
    }

    @GetMapping("/{moduleId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<AssessmentResponse>
    getAssessment(
            @PathVariable @jakarta.validation.constraints.Positive Long moduleId,
            Authentication authentication) {

        UserEntity student =
                userRepository
                        .findByEmail(
                                authentication.getName()
                        )
                        .orElseThrow(() ->
                                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED")
                        );

        return ResponseEntity.ok(
                assessmentService.getAssessment(
                        moduleId,
                        student
                )
        );
    }
    @PostMapping("/start/{moduleId}")
    public ResponseEntity<AssessmentAttemptResponse> startAttempt(
            @PathVariable @jakarta.validation.constraints.Positive Long moduleId,
            Authentication authentication) {

        String email = authentication.getName();

        UserEntity student =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED")
                        );

        AssessmentAttemptEntity attempt =
                assessmentService.startAttempt(
                        moduleId,
                        student
                );

        AssessmentAttemptResponse response =
                new AssessmentAttemptResponse();

        response.setAttemptId(attempt.getId());
        response.setAssessmentId(
                attempt.getAssessment().getId()
        );
        response.setStartedAt(
                attempt.getStartedAt()
        );

        return ResponseEntity.ok(response);
    }
    @PostMapping("/submit/{attemptId}")
    public ResponseEntity<AssessmentResultResponse> submitAttempt(
            @PathVariable @jakarta.validation.constraints.Positive Long attemptId,
            @jakarta.validation.Valid @RequestBody StudentAnswerRequest request,
            Authentication authentication) {

        String email = authentication.getName();

        UserEntity student =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED")
                        );

        AssessmentAttemptEntity attempt =
                assessmentService.submitAttempt(
                        attemptId,
                        student,
                        request
                );

        AssessmentResultResponse response =
                new AssessmentResultResponse();

        response.setAttemptId(attempt.getId());
        response.setScore(attempt.getScore());
        response.setPassed(attempt.getPassed());

        return ResponseEntity.ok(response);
    }
    @GetMapping("/attempts/{moduleId}")
    public ResponseEntity<List<AssessmentAttemptHistoryResponse>> getAttemptHistory(
            @PathVariable @jakarta.validation.constraints.Positive Long moduleId,
            Authentication authentication) {

        String email = authentication.getName();

        UserEntity student =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED")
                        );

        List<AssessmentAttemptHistoryResponse> response =
                assessmentService.getAttemptHistory(
                        moduleId,
                        student
                );

        return ResponseEntity.ok(response);
    }
    @GetMapping("/result/{attemptId}")
    public ResponseEntity<AssessmentResultResponse> getAttemptResult(
            @PathVariable @jakarta.validation.constraints.Positive Long attemptId,
            Authentication authentication) {

        String email = authentication.getName();

        UserEntity student =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED")
                        );

        AssessmentResultResponse response =
                assessmentService.getAttemptResult(
                        attemptId,
                        student
                );

        return ResponseEntity.ok(response);
    }
    @GetMapping("/status/{moduleId}")
    public ResponseEntity<AssessmentStatusResponse> getAssessmentStatus(
            @PathVariable @jakarta.validation.constraints.Positive Long moduleId,
            Authentication authentication) {

        String email = authentication.getName();

        UserEntity student =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED")
                        );

        AssessmentStatusResponse response =
                assessmentService.getAssessmentStatus(
                        moduleId,
                        student
                );

        return ResponseEntity.ok(response);
    }
}