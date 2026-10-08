package com.ptc.halo.controller;

import com.ptc.halo.dtoRequest.AiLearningModuleUpdateRequest;
import com.ptc.halo.dtoResponse.AiLearningModuleResponse;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.repository.AiLearningFileRepository;
import com.ptc.halo.repository.AiLearningModuleRepository;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.repository.WeekRepository;
import com.ptc.halo.service.ActivityLogService;
import com.ptc.halo.service.AiGenerationService;
import com.ptc.halo.service.AssessmentService;
import com.ptc.halo.service.FileUploadService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/professor/ai-learning-modules")
@PreAuthorize("hasRole('PROFESSOR')")
public class AiLearningModuleController {

    private final com.ptc.halo.service.ProfessorModuleService workflows;
    private final UserRepository userRepository;

    // Retained for direct construction in existing controller tests. Spring uses the service constructor below.
    public AiLearningModuleController(
            AiLearningModuleRepository aiLearningModuleRepository,
            WeekRepository weekRepository,
            FileUploadService fileUploadService,
            AiGenerationService aiGenerationService,
            AiLearningFileRepository aiLearningFileRepository,
            AssessmentService assessmentService,
            ActivityLogService activityLogService,
            UserRepository userRepository, com.ptc.halo.component.ModuleMaterialIndex materialIndex
    ) {
        this.userRepository = userRepository;
        this.workflows = new com.ptc.halo.service.ProfessorModuleService(aiLearningModuleRepository, weekRepository, fileUploadService, aiGenerationService, aiLearningFileRepository, assessmentService, activityLogService, userRepository, materialIndex);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AiLearningModuleController(com.ptc.halo.service.ProfessorModuleService workflows, UserRepository users) {
        this.workflows = workflows; this.userRepository = users;
    }

    @InitBinder
    public void trimText(org.springframework.web.bind.WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new org.springframework.beans.propertyeditors.StringTrimmerEditor(false));
    }

    // Returns the existing module, including drafts.
    // 204 means this week does not have a module yet.
    @GetMapping("/week/{weekId}")
    @Transactional(readOnly = true)
    public ResponseEntity<AiLearningModuleResponse> getModuleByWeek(
            @PathVariable("weekId") @Positive Long weekId, Authentication authentication
    ) {
        return workflows.getModuleByWeek(weekId, getCurrentUser(authentication), null);
    }

    @PostMapping(consumes = "multipart/form-data")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<AiLearningModuleResponse> createModule(
            @RequestParam("weekId") @Positive Long weekId,
            @RequestParam(value = "lessonText", required = false)
            @jakarta.validation.constraints.Size(max = 100000)
            String lessonText,
            @RequestParam(value = "youtubeLink", required = false)
            @jakarta.validation.constraints.Size(max = 255)
            @org.hibernate.validator.constraints.URL(regexp = "https?://.+", message = "must be a valid HTTP(S) URL")
            String youtubeLink,
            @RequestParam(value = "aiNotes", required = false)
            @jakarta.validation.constraints.Size(max = 10000)
            String aiNotes,
            @RequestParam(value = "files", required = false)
            List<MultipartFile> files,
            Authentication authentication
    ) throws Exception {
        return workflows.createModule(weekId, lessonText, youtubeLink, aiNotes, files, getCurrentUser(authentication), null);
    }

    @PostMapping("/{id}/generate")
    public ResponseEntity<AiLearningModuleResponse> generateLesson(
            @PathVariable("id") @Positive Long id,
            Authentication authentication
    ) {
        return workflows.generateLesson(id, getCurrentUser(authentication), null);
    }

    @PutMapping("/{id}/approve")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<AiLearningModuleResponse> approveLesson(
            @PathVariable("id") @Positive Long id,
            Authentication authentication
    ) {
        return workflows.approveLesson(id, getCurrentUser(authentication), null);
    }

    @PutMapping("/{id}/decline")
    @Transactional
    public ResponseEntity<AiLearningModuleResponse> declineLesson(
            @PathVariable("id") @Positive Long id,
            Authentication authentication
    ) {
        return workflows.declineLesson(id, getCurrentUser(authentication), null);
    }

    @PostMapping(
            path = "/{moduleId}/files",
            consumes = "multipart/form-data"
    )
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<AiLearningModuleResponse> uploadFile(
            @PathVariable("moduleId") @Positive Long moduleId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws Exception {
        return workflows.uploadFile(moduleId, file, getCurrentUser(authentication), null);
    }

    @DeleteMapping("/files/{fileId}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<String> deleteFile(
            @PathVariable("fileId") @Positive Long fileId,
            Authentication authentication
    ) throws Exception {
        return workflows.deleteFile(fileId, getCurrentUser(authentication), null);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<AiLearningModuleResponse> updateModule(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody AiLearningModuleUpdateRequest request,
            Authentication authentication
    ) {
        return workflows.updateModule(id, request, getCurrentUser(authentication), null);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<AiLearningModuleResponse> getModuleById(
            @PathVariable("id") @Positive Long id, Authentication authentication) {
        return workflows.getModuleById(id, getCurrentUser(authentication), null);
    }

    private UserEntity getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Please sign in."
            );
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "User not found"
                        )
                );
    }

    // Return explicit JSON errors instead of forwarding these failures
    // to the generic error page.
    // MVC method-validation exceptions also extend ResponseStatusException;
    // handle them explicitly before this controller's business-error handler.
    @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
    public ResponseEntity<?> validation(org.springframework.web.method.annotation.HandlerMethodValidationException error) {
        return new ProfessorValidationHandler().parameters(error);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleRequestError(
            ResponseStatusException exception
    ) {
        return ProfessorErrorResponses.status(exception);
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> staleModule() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status", 409, "code", "STALE_MODULE_OPERATION",
                "message", "This module changed while the operation was running. Reload it before trying again."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseConflict(
            DataIntegrityViolationException exception
    ) {
        return ProfessorErrorResponses.reply(409, "RESOURCE_CONFLICT");
    }
}