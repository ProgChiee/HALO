package com.ptc.halo.service;
import com.ptc.halo.controller.*;
import com.ptc.halo.entity.AdminActingSessionEntity;

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

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service


public class ProfessorModuleService {

    private final AiLearningModuleRepository aiLearningModuleRepository;
    private final WeekRepository weekRepository;
    private final FileUploadService fileUploadService;
    private final AiGenerationService aiGenerationService;
    private final AiLearningFileRepository aiLearningFileRepository;
    private final AssessmentService assessmentService;
    private final ActivityLogService activityLogService;
    private final UserRepository userRepository;
    private final com.ptc.halo.component.ModuleMaterialIndex materialIndex;

    public ProfessorModuleService(
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

    // Returns the existing module, including drafts.
    // 204 means this week does not have a module yet.
    @Transactional(readOnly = true)
    public ResponseEntity<AiLearningModuleResponse> getModuleByWeek(
            Long weekId, UserEntity professor, AdminActingSessionEntity acting
    ) {
        requireWeek(weekId, professor);

        return aiLearningModuleRepository.findByWeekId(weekId)
                .map(module ->
                        ResponseEntity.ok(convertToResponse(module))
                )
                .orElseGet(() ->
                        ResponseEntity.noContent().build()
                );
    }

    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<AiLearningModuleResponse> createModule(
            Long weekId,
            String lessonText,
            String youtubeLink,
            String aiNotes,
            List<MultipartFile> files,
            UserEntity professor, AdminActingSessionEntity acting
    ) throws Exception {


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

        log(
                professor, acting,
                ActivityType.MODULE,
                "Created learning module ID " + savedModule.getId()
        );

        return ResponseEntity.ok(convertToResponse(savedModule));
    }

    public ResponseEntity<AiLearningModuleResponse> generateLesson(
            Long id,
            UserEntity professor, AdminActingSessionEntity acting
    ) {
        AiLearningModuleEntity module = findModule(id, professor);

        requireEditable(module);

        AiLearningModuleEntity generatedModule =
                aiGenerationService.generateLesson(id);

        log(
                professor, acting,
                ActivityType.MODULE,
                "Generated AI lesson for module ID " + id
        );

        return ResponseEntity.ok(
                convertToResponse(generatedModule)
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<AiLearningModuleResponse> approveLesson(
            Long id,
            UserEntity professor, AdminActingSessionEntity acting
    ) {
        AiLearningModuleEntity module = findModule(id, professor);

        requireApprovalReady(module);
        // Same module-only readiness checks as Mentor, before approval or assessment writes.
        materialIndex.getOrBuild(module);
        module.setStatus(LessonStatus.APPROVED);

        AiLearningModuleEntity savedModule =
                aiLearningModuleRepository.save(module);

        // If this throws, participating database changes roll back.
        assessmentService.generateAssessment(savedModule.getId());

        log(
                professor, acting,
                ActivityType.MODULE,
                "Approved AI lesson for module ID " + id
        );

        return ResponseEntity.ok(convertToResponse(savedModule));
    }

    @Transactional
    public ResponseEntity<AiLearningModuleResponse> declineLesson(
            Long id,
            UserEntity professor, AdminActingSessionEntity acting
    ) {
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

        log(
                professor, acting,
                ActivityType.MODULE,
                "Declined AI lesson for module ID " + id
        );

        return ResponseEntity.ok(convertToResponse(savedModule));
    }

    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<AiLearningModuleResponse> uploadFile(
            Long moduleId,
            MultipartFile file,
            UserEntity professor, AdminActingSessionEntity acting
    ) throws Exception {
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

        log(
                professor, acting,
                ActivityType.MODULE,
                "Uploaded file to learning module ID " + moduleId
        );

        return ResponseEntity.ok(convertToResponse(module));
    }

    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<String> deleteFile(
            Long fileId,
            UserEntity professor, AdminActingSessionEntity acting
    ) throws Exception {

        AiLearningFileEntity file = aiLearningFileRepository
                .findByIdAndModule_Week_Subject_Professor_User_Id(fileId, professor.getId())
                .orElseThrow(this::resourceNotFound);

        AiLearningModuleEntity module = file.getModule();
        requireEditable(module);

        fileUploadService.deleteFile(file.getFilePath());

        module.removeFile(file);
        invalidateGeneratedLesson(module);
        aiLearningModuleRepository.saveAndFlush(module);

        log(
                professor, acting,
                ActivityType.MODULE,
                "Removed file from learning module ID " + module.getId()
        );

        return ResponseEntity.ok("File deleted successfully");
    }

    @Transactional
    public ResponseEntity<AiLearningModuleResponse> updateModule(
            Long id,
            AiLearningModuleUpdateRequest request,
            UserEntity professor, AdminActingSessionEntity acting
    ) {
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

        log(
                professor, acting,
                ActivityType.MODULE,
                "Updated learning module ID " + id
        );

        return ResponseEntity.ok(convertToResponse(updatedModule));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<AiLearningModuleResponse> getModuleById(
            Long id, UserEntity professor, AdminActingSessionEntity acting) {
        return ResponseEntity.ok(convertToResponse(findModule(id, professor)));
    }

    private WeekEntity requireWeek(Long id, UserEntity professor) {
        return weekRepository.findByIdAndSubject_Professor_User_Id(id, professor.getId())
                .orElseThrow(this::resourceNotFound);
    }

    public AiLearningModuleEntity findModule(Long id, UserEntity professor) {
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

    public AiLearningModuleResponse convertToResponse(
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

    private void log(UserEntity professor, com.ptc.halo.entity.AdminActingSessionEntity acting, ActivityType type, String action) {
        if (acting == null) activityLogService.createLog(professor, type, action);
        else activityLogService.createProfessorLog(professor, acting, type, action);
    }
    public void requireApprovalReady(AiLearningModuleEntity module) {
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
    }
}
