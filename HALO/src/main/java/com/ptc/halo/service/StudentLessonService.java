package com.ptc.halo.service;
import com.ptc.halo.entity.*;
import com.ptc.halo.dtoResponse.AiLearningModuleResponse;
import com.ptc.halo.repository.AiLearningModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class StudentLessonService {
    private final AiLearningModuleRepository modules;
    private final StudentLearningProgressionService progression;
    public StudentLessonService(AiLearningModuleRepository modules, StudentLearningProgressionService progression) {
        this.modules=modules; this.progression=progression;
    }
    @Transactional(readOnly=true)
    public AiLearningModuleResponse byWeek(Long weekId, UserEntity student) {
        var module=modules.findByWeekId(weekId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"MODULE_NOT_FOUND"));
        progression.validateModuleAccess(module.getId(),student);
        return convertToResponse(module);
    }
    private AiLearningModuleResponse convertToResponse(
            AiLearningModuleEntity module) {

        AiLearningModuleResponse response =
                new AiLearningModuleResponse();

        response.setId(module.getId());
        response.setWeekId(module.getWeek().getId());
        response.setSubjectId(module.getWeek().getSubject().getId());
        response.setWeekNumber(module.getWeek().getWeekNumber());

        response.setYoutubeLink(
                module.getYoutubeLink()
        );

        response.setLessonText(
                module.getLessonText()
        );

        response.setGeneratedObjectives(
                module.getGeneratedObjectives()
        );

        response.setGeneratedKnowledge(
                module.getGeneratedKnowledge()
        );

        response.setGeneratedExamples(
                module.getGeneratedExamples()
        );

        response.setGeneratedSummary(
                module.getGeneratedSummary()
        );

        response.setStatus(
                module.getStatus()
        );

        response.setAiGenerationStatus(
                module.getAiGenerationStatus()
        );

        return response;
    }
}
