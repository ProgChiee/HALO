package com.ptc.halo.service;

import com.ptc.halo.entity.AiLearningModuleEntity;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.AiLearningModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Objects;
import java.util.UUID;

/** Short persistence phases only; external generation runs between these transactions. */
@Service
public class ModuleGenerationState {
    private final AiLearningModuleRepository modules;
    public ModuleGenerationState(AiLearningModuleRepository modules) { this.modules = modules; }

    @Transactional
    public AiLearningModuleEntity begin(Long id) {
        var module = modules.findWithFilesById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND"));
        if (module.getStatus() == LessonStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Published lessons cannot be regenerated through the draft editor.");
        }
        module.setGenerationToken(UUID.randomUUID().toString()); // Forces a new version even when already PENDING.
        module.setStatus(LessonStatus.PENDING);
        module.setAiGenerationStatus(AiGenerationStatus.PENDING);
        module.setGeneratedObjectives(null);
        module.setGeneratedKnowledge(null);
        module.setGeneratedExamples(null);
        module.setGeneratedSummary(null);
        return modules.saveAndFlush(module);
    }

    @Transactional
    public AiLearningModuleEntity finish(Long id, AiLearningModuleEntity result) {
        var current = modules.findWithFilesById(id).orElseThrow(this::stale);
        if (!Objects.equals(current.getVersion(), result.getVersion())
                || result.getGenerationToken() == null
                || !Objects.equals(current.getGenerationToken(), result.getGenerationToken())
                || current.getStatus() != LessonStatus.PENDING
                || current.getAiGenerationStatus() != AiGenerationStatus.PENDING) throw stale();
        // Copy generated fields only. Never merge detached materials, files, or publication state.
        current.setGeneratedObjectives(result.getGeneratedObjectives());
        current.setGeneratedKnowledge(result.getGeneratedKnowledge());
        current.setGeneratedExamples(result.getGeneratedExamples());
        current.setGeneratedSummary(result.getGeneratedSummary());
        current.setAiGenerationStatus(result.getAiGenerationStatus());
        current.setGenerationToken(null);
        return modules.saveAndFlush(current); // @Version also detects races after the check.
    }

    private ResponseStatusException stale() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "STALE_MODULE_OPERATION");
    }
}
