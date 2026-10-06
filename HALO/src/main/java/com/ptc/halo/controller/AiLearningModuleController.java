package com.ptc.halo.controller;

import com.ptc.halo.dtoRequest.AiLearningModuleUpdateRequest;
import com.ptc.halo.dtoResponse.AiLearningFileResponse;
import com.ptc.halo.dtoResponse.AiLearningModuleResponse;
import com.ptc.halo.entity.AiLearningFileEntity;
import com.ptc.halo.entity.AiLearningModuleEntity;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.entity.WeekEntity;
import com.ptc.halo.enums.ActivityType;
import com.ptc.halo.enums.AiGenerationStatus;
import com.ptc.halo.enums.LessonStatus;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/professor/ai-learning-modules")
@PreAuthorize("hasRole('PROFESSOR')")
public class AiLearningModuleController {

    private final AiLearningModuleRepository aiLearningModuleRepository;
    private final WeekRepository weekRepository;
    private final FileUploadService fileUploadService;
    private final AiGenerationService aiGenerationService;
    private final AiLearningFileRepository aiLearningFileRepository;
    private final AssessmentService assessmentService;
    private final ActivityLogService activityLogService;
    private final UserRepository userRepository;
    private final com.ptc.halo.component.ModuleMaterialIndex materialIndex;

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
        this.aiLearningModuleRepository = aiLearningModuleRepository;
        this.weekRepository = weekRepository;
        this.fileUploadService = fileUploadService;
        this.aiGenerationService = aiGenerationService;
        this.aiLearningFileRepository = aiLearningFileRepository;
        this.assessmentService = assessmentService;
        this.activityLogService = activityLogService;
        this.userRepository = userRepository;
        this.materialIndex = materialIndex;
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
        requireWeek(weekId, getCurrentUser(authentication));

        return aiLearningModuleRepository.findByWeekId(weekId)
                .map(module ->
                        ResponseEntity.ok(convertToResponse(module))
                )
                .orElseGet(() ->
                        ResponseEntity.noContent().build()
                );
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

        UserEntity professor = getCurrentUser(authentication);

        WeekEntity week = requireWeek(weekId, professor);

        // Check before writing any uploaded files.
        if (aiLearningModuleRepository.findByWeekId(weekId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This week already has a module. Open the existing module."
            );
        }

        var validatedFiles = fileUploadService.validateFiles(files);

        boolean hasFiles = files != null
                && files.stream().anyMatch(file ->
                file != null && !file.isEmpty()
        );

        if (!hasText(lessonText) && !hasText(youtubeLink) && !hasFiles) {
            throw new ProfessorInputException("sources", "Add lesson text, a valid link, or a file.");
        }

        AiLearningModuleEntity module = new AiLearningModuleEntity();

        module.setWeek(week);
        module.setLessonText(lessonText == null ? null : lessonText.strip());
        module.setYoutubeLink(youtubeLink == null ? null : youtubeLink.strip());
        module.setAiNotes(aiNotes == null ? null : aiNotes.strip());
        module.setStatus(LessonStatus.PENDING);
        module.setAiGenerationStatus(AiGenerationStatus.PENDING);

        for (var file : validatedFiles) {
            module.addFile(fileUploadService.uploadValidated(file, module));
        }

        // Flush here so database conflicts are raised before returning.
        AiLearningModuleEntity savedModule =
                aiLearningModuleRepository.saveAndFlush(module);

        activityLogService.createLog(
                professor,
                ActivityType.MODULE,
                "Created learning module ID " + savedModule.getId()
        );

        return ResponseEntity.ok(convertToResponse(savedModule));
    }

    @PostMapping("/{id}/generate")
    public ResponseEntity<AiLearningModuleResponse> generateLesson(
            @PathVariable("id") @Positive Long id,
            Authentication authentication
    ) {
        UserEntity professor = getCurrentUser(authentication);
        AiLearningModuleEntity module = findModule(id, professor);

        requireEditable(module);

        AiLearningModuleEntity generatedModule =
                aiGenerationService.generateLesson(id);

        activityLogService.createLog(
                professor,
                ActivityType.MODULE,
                "Generated AI lesson for module ID " + id
        );

        return ResponseEntity.ok(
                convertToResponse(generatedModule)
        );
    }

    @PutMapping("/{id}/approve")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<AiLearningModuleResponse> approveLesson(
            @PathVariable("id") @Positive Long id,
            Authentication authentication
    ) {
        UserEntity professor = getCurrentUser(authentication);
        AiLearningModuleEntity module = findModule(id, professor);

        if (module.getStatus() == LessonStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "MODULE_ALREADY_APPROVED"
            );
        }

        if (module.getAiGenerationStatus() != AiGenerationStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "MODULE_GENERATION_REQUIRED"
            );
        }

        if (module.getFiles() == null || module.getFiles().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MODULE_MATERIALS_REQUIRED");
        }
        // Same module-only readiness checks as Mentor, before approval or assessment writes.
        materialIndex.getOrBuild(module);
        module.setStatus(LessonStatus.APPROVED);

        AiLearningModuleEntity savedModule =
                aiLearningModuleRepository.save(module);

        // If this throws, participating database changes roll back.
        assessmentService.generateAssessment(savedModule.getId());

        activityLogService.createLog(
                professor,
                ActivityType.MODULE,
                "Approved AI lesson for module ID " + id
        );

        return ResponseEntity.ok(convertToResponse(savedModule));
    }

    @PutMapping("/{id}/decline")
    @Transactional
    public ResponseEntity<AiLearningModuleResponse> declineLesson(
            @PathVariable("id") @Positive Long id,
            Authentication authentication
    ) {
        UserEntity professor = getCurrentUser(authentication);
        AiLearningModuleEntity module = findModule(id, professor);

        requireEditable(module);

        if (module.getAiGenerationStatus() != AiGenerationStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "MODULE_GENERATION_REQUIRED"
            );
        }

        module.setStatus(LessonStatus.DECLINED);

        AiLearningModuleEntity savedModule =
                aiLearningModuleRepository.save(module);

        activityLogService.createLog(
                professor,
                ActivityType.MODULE,
                "Declined AI lesson for module ID " + id
        );

        return ResponseEntity.ok(convertToResponse(savedModule));
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
        UserEntity professor = getCurrentUser(authentication);
        AiLearningModuleEntity module = findModule(moduleId, professor);

        requireEditable(module);

        AiLearningFileEntity fileEntity =
                fileUploadService.uploadFile(file, module);

        module.addFile(fileEntity);
        aiLearningFileRepository.save(fileEntity);

        // Materials changed: regenerate before approving.
        invalidateGeneratedLesson(module);
        // Force a versioned row update even if the module was already pending.
        // Concurrent uploads based on the same attachment count cannot both commit.
        module.setGenerationToken(java.util.UUID.randomUUID().toString());
        aiLearningModuleRepository.saveAndFlush(module);

        activityLogService.createLog(
                professor,
                ActivityType.MODULE,
                "Uploaded file to learning module ID " + moduleId
        );

        return ResponseEntity.ok(convertToResponse(module));
    }

    @DeleteMapping("/files/{fileId}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<String> deleteFile(
            @PathVariable("fileId") @Positive Long fileId,
            Authentication authentication
    ) throws Exception {
        UserEntity professor = getCurrentUser(authentication);

        AiLearningFileEntity file = aiLearningFileRepository
                .findByIdAndModule_Week_Subject_Professor_User_Id(fileId, professor.getId())
                .orElseThrow(this::resourceNotFound);

        AiLearningModuleEntity module = file.getModule();
        requireEditable(module);

        fileUploadService.deleteFile(file.getFilePath());

        module.removeFile(file);
        invalidateGeneratedLesson(module);
        aiLearningModuleRepository.saveAndFlush(module);

        activityLogService.createLog(
                professor,
                ActivityType.MODULE,
                "Removed file from learning module ID " + module.getId()
        );

        return ResponseEntity.ok("File deleted successfully");
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<AiLearningModuleResponse> updateModule(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody AiLearningModuleUpdateRequest request,
            Authentication authentication
    ) {
        UserEntity professor = getCurrentUser(authentication);
        AiLearningModuleEntity module = findModule(id, professor);

        requireEditable(module);

        if (!hasText(request.getLessonText()) && !hasText(request.getYoutubeLink()) && module.getFiles().isEmpty()) {
            throw new ProfessorInputException("sources", "Keep lesson text, a valid link, or an attached file.");
        }
        module.setLessonText(request.getLessonText());
        module.setYoutubeLink(request.getYoutubeLink());
        module.setAiNotes(request.getAiNotes());

        invalidateGeneratedLesson(module);

        AiLearningModuleEntity updatedModule =
                aiLearningModuleRepository.save(module);

        activityLogService.createLog(
                professor,
                ActivityType.MODULE,
                "Updated learning module ID " + id
        );

        return ResponseEntity.ok(convertToResponse(updatedModule));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<AiLearningModuleResponse> getModuleById(
            @PathVariable("id") @Positive Long id, Authentication authentication) {
        return ResponseEntity.ok(convertToResponse(findModule(id, getCurrentUser(authentication))));
    }

    private WeekEntity requireWeek(Long id, UserEntity professor) {
        return weekRepository.findByIdAndSubject_Professor_User_Id(id, professor.getId())
                .orElseThrow(this::resourceNotFound);
    }

    private AiLearningModuleEntity findModule(Long id, UserEntity professor) {
        return aiLearningModuleRepository.findByIdAndWeek_Subject_Professor_User_Id(id, professor.getId())
                .orElseThrow(this::resourceNotFound);
    }

    private ResponseStatusException resourceNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    private void requireEditable(AiLearningModuleEntity module) {
        if (module.getStatus() == LessonStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "MODULE_NOT_EDITABLE"
            );
        }
    }

    private void invalidateGeneratedLesson(AiLearningModuleEntity module) {
        module.setGenerationToken(null);
        module.setStatus(LessonStatus.PENDING);
        module.setAiGenerationStatus(AiGenerationStatus.PENDING);
        module.setGeneratedObjectives(null);
        module.setGeneratedKnowledge(null);
        module.setGeneratedExamples(null);
        module.setGeneratedSummary(null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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

    private AiLearningModuleResponse convertToResponse(
            AiLearningModuleEntity module
    ) {
        AiLearningModuleResponse response =
                new AiLearningModuleResponse();

        response.setId(module.getId());
        response.setWeekId(module.getWeek().getId());
        response.setYoutubeLink(module.getYoutubeLink());
        response.setAiNotes(module.getAiNotes());
        response.setLessonText(module.getLessonText());
        response.setGeneratedObjectives(module.getGeneratedObjectives());
        response.setGeneratedKnowledge(module.getGeneratedKnowledge());
        response.setGeneratedExamples(module.getGeneratedExamples());
        response.setGeneratedSummary(module.getGeneratedSummary());
        response.setStatus(module.getStatus());
        response.setAiGenerationStatus(module.getAiGenerationStatus());

        response.setFiles(
                module.getFiles().stream()
                        .map(file -> {
                            AiLearningFileResponse fileResponse =
                                    new AiLearningFileResponse();

                            fileResponse.setId(file.getId());
                            fileResponse.setOriginalFileName(
                                    file.getOriginalFileName()
                            );
                            fileResponse.setStoredFileName(
                                    file.getStoredFileName()
                            );
                            fileResponse.setFileType(file.getFileType());
                            fileResponse.setFilePath(file.getFilePath());

                            return fileResponse;
                        })
                        .collect(Collectors.toList())
        );

        return response;
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