package com.ptc.halo.controller;

import com.ptc.halo.dtoRequest.SubjectRequest;
import com.ptc.halo.dtoRequest.SubjectUpdateRequest;
import com.ptc.halo.dtoRequest.WeekRequest;
import com.ptc.halo.dtoRequest.WeekUpdateRequest;
import com.ptc.halo.dtoResponse.SubjectResponse;
import com.ptc.halo.dtoResponse.WeekResponse;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.service.ProfessorAcademicService;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/professor")
@PreAuthorize("hasRole('PROFESSOR')")
public class ProfessorAcademicController {

    private final ProfessorAcademicService professorAcademicService;
    private final UserRepository userRepository;

    public ProfessorAcademicController(
            ProfessorAcademicService professorAcademicService,
            UserRepository userRepository) {

        this.professorAcademicService = professorAcademicService;
        this.userRepository = userRepository;
    }


    // =========================
    // SUBJECTS
    // =========================

    @PostMapping("/subjects")
    public ResponseEntity<SubjectResponse> createSubject(
            @Valid @RequestBody SubjectRequest request,
            Authentication authentication) {

        UserEntity professor =
                getCurrentProfessor(authentication);

        return ResponseEntity.ok(
                professorAcademicService.createSubject(
                        request,
                        professor
                )
        );
    }


    @GetMapping("/subjects")
    public ResponseEntity<List<SubjectResponse>>
    viewAllSubjects(Authentication authentication) {

        return ResponseEntity.ok(
                professorAcademicService.viewAllSubjects(getCurrentProfessor(authentication))
        );
    }


    @GetMapping("/subjects/{id}")
    public ResponseEntity<SubjectResponse> viewSubjectById(
            @PathVariable("id") @Positive Long id, Authentication authentication) {

        return ResponseEntity.ok(
                professorAcademicService.viewSubjectById(id, getCurrentProfessor(authentication))
        );
    }


    @PutMapping("/subjects/{id}")
    public ResponseEntity<SubjectResponse> updateSubject(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody SubjectUpdateRequest request,
            Authentication authentication) {

        UserEntity professor =
                getCurrentProfessor(authentication);

        return ResponseEntity.ok(
                professorAcademicService.updateSubject(
                        id,
                        request,
                        professor
                )
        );
    }


    @DeleteMapping("/subjects/{id}")
    public ResponseEntity<String> deleteSubject(
            @PathVariable("id") @Positive Long id,
            Authentication authentication) {

        UserEntity professor =
                getCurrentProfessor(authentication);

        professorAcademicService.deleteSubject(
                id,
                professor
        );

        return ResponseEntity.ok(
                "Subject deleted successfully"
        );
    }


    // =========================
    // WEEKS
    // =========================

    @PostMapping("/subjects/{subjectId}/weeks")
    public ResponseEntity<WeekResponse> createWeek(
            @PathVariable("subjectId") @Positive Long subjectId,
            @Valid @RequestBody WeekRequest request,
            Authentication authentication) {

        UserEntity professor =
                getCurrentProfessor(authentication);

        return ResponseEntity.ok(
                professorAcademicService.createWeek(
                        subjectId,
                        request,
                        professor
                )
        );
    }


    @GetMapping("/subjects/{subjectId}/weeks")
    public ResponseEntity<List<WeekResponse>> viewAllWeeks(
            @PathVariable("subjectId") @Positive Long subjectId, Authentication authentication) {

        return ResponseEntity.ok(
                professorAcademicService.viewAllWeeks(
                        subjectId, getCurrentProfessor(authentication)
                )
        );
    }


    @GetMapping("/weeks/{id}")
    public ResponseEntity<WeekResponse> viewWeekById(
            @PathVariable("id") @Positive Long id, Authentication authentication) {

        return ResponseEntity.ok(
                professorAcademicService.viewWeekById(id, getCurrentProfessor(authentication))
        );
    }


    @PutMapping("/weeks/{id}")
    public ResponseEntity<WeekResponse> updateWeek(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody WeekUpdateRequest request,
            Authentication authentication) {

        UserEntity professor =
                getCurrentProfessor(authentication);

        return ResponseEntity.ok(
                professorAcademicService.updateWeek(
                        id,
                        request,
                        professor
                )
        );
    }


    @DeleteMapping("/weeks/{id}")
    public ResponseEntity<String> deleteWeek(
            @PathVariable("id") @Positive Long id,
            Authentication authentication) {

        UserEntity professor =
                getCurrentProfessor(authentication);

        professorAcademicService.deleteWeek(
                id,
                professor
        );

        return ResponseEntity.ok(
                "Week deleted successfully"
        );
    }


    // =========================
    // CURRENT PROFESSOR
    // =========================

    // MVC method-validation exceptions also extend ResponseStatusException;
    // handle them explicitly before this controller's business-error handler.
    @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
    public ResponseEntity<?> validation(org.springframework.web.method.annotation.HandlerMethodValidationException error) {
        return new ProfessorValidationHandler().parameters(error);
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<java.util.Map<String, Object>> scopedError(org.springframework.web.server.ResponseStatusException error) {
        if (error.getStatusCode().value() == 409 && "WEEK_NUMBER_ALREADY_EXISTS".equals(error.getReason())) {
            return ResponseEntity.status(409).body(java.util.Map.of(
                    "status", 409, "code", "WEEK_NUMBER_ALREADY_EXISTS",
                    "message", "This subject already has that week number. Choose another week number."));
        }
        if (error.getStatusCode().value() == 409 && "SUBJECT_HAS_CONTENT".equals(error.getReason())) {
            return ResponseEntity.status(409).body(java.util.Map.of(
                    "status", 409, "code", "SUBJECT_HAS_CONTENT",
                    "message", "This subject still contains weeks or learning modules. Remove its content before deleting the subject."));
        }
        return ResponseEntity.status(error.getStatusCode()).body(java.util.Map.of(
                "status", error.getStatusCode().value(), "code", "RESOURCE_NOT_FOUND",
                "message", "The requested resource is unavailable."));
    }

    private UserEntity getCurrentProfessor(
            Authentication authentication) {

        return userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Professor not found"
                        )
                );
    }
}
