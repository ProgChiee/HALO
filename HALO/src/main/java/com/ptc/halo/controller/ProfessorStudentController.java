package com.ptc.halo.controller;

import com.ptc.halo.dtoResponse.*;
import com.ptc.halo.service.ProfessorStudentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/professor/students")
public class ProfessorStudentController {

    private final UserRepository users;

    private final ProfessorStudentService
            professorStudentService;

    public ProfessorStudentController(
            ProfessorStudentService professorStudentService, UserRepository users) {
        this.users = users;

        this.professorStudentService =
                professorStudentService;
    }


    @PreAuthorize("hasRole('PROFESSOR')")
    @GetMapping
    public ResponseEntity<List<ProfessorStudentResponse>>
    getStudents(Authentication authentication) {

        return ResponseEntity.ok(
                professorStudentService.getStudents(currentProfessor(authentication))
        );
    }
    @PreAuthorize("hasRole('PROFESSOR')")
    @GetMapping("/{userId}/progress")
    public ResponseEntity<ProfessorStudentProgressResponse>
    getStudentProgress(
            @PathVariable("userId") Long userId, Authentication authentication) {

        return ResponseEntity.ok(
                professorStudentService
                        .getStudentProgress(userId, currentProfessor(authentication))
        );
    }
    @PreAuthorize("hasRole('PROFESSOR')")
    @GetMapping("/progress-summaries")
    public ResponseEntity<Map<String, Object>> getProgressSummaries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Authentication authentication) {
        var result = professorStudentService.getProgressSummaries(currentProfessor(authentication), page, size);
        return ResponseEntity.ok(Map.of("content", result.getContent(), "number", result.getNumber(),
                "size", result.getSize(), "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(), "first", result.isFirst(), "last", result.isLast(),
                "badgeScope", "INSTITUTION_WIDE"));
    }
    @PreAuthorize("hasRole('PROFESSOR')")
    @GetMapping("/{userId}/subjects")
    public ResponseEntity<List<StudentSubjectResponse>>
    getStudentSubjects(
            @PathVariable("userId") Long userId, Authentication authentication) {

        return ResponseEntity.ok(
                professorStudentService
                        .getStudentSubjects(userId, currentProfessor(authentication))
        );
    }
    @PreAuthorize("hasRole('PROFESSOR')")
    @GetMapping("/{userId}/assessments")
    public ResponseEntity<List<ProfessorStudentAssessmentResponse>>
    getStudentAssessmentHistory(
            @PathVariable("userId") Long userId, Authentication authentication) {

        return ResponseEntity.ok(
                professorStudentService
                        .getStudentAssessmentHistory(userId, currentProfessor(authentication))
        );
    }
    @PreAuthorize("hasRole('PROFESSOR')")
    @GetMapping("/{userId}/badges")
    public ResponseEntity<List<ProfessorStudentBadgeResponse>>
    getStudentBadges(
            @PathVariable("userId") Long userId, Authentication authentication) {

        return ResponseEntity.ok(
                professorStudentService
                        .getStudentBadges(userId, currentProfessor(authentication))
        );
    }
    private UserEntity currentProfessor(Authentication authentication) {
        if (authentication == null) throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Authentication required");
        return users.findByEmail(authentication.getName()).orElseThrow(() -> new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Authentication required"));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> notFound(ResponseStatusException error) {
        return ProfessorErrorResponses.status(error);
    }
}
