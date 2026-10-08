package com.ptc.halo.service;

import com.ptc.halo.entity.*;
import com.ptc.halo.dtoResponse.AiLearningModuleResponse;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.ProfessorRepository;
import com.ptc.halo.component.ModuleMaterialIndex;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Objects;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminProfessorModeService {
    private final AdminActingSessionService sessions;
    private final ProfessorRepository professors;
    private final ProfessorModuleService modules;
    private final ModuleGenerationState generationState;
    private final AiGenerationService generation;
    private final AssessmentService assessments;
    private final ActivityLogService audit;
    private final ModuleMaterialIndex index;
    private final TransactionTemplate transactions;
    public AdminProfessorModeService(AdminActingSessionService sessions, ProfessorRepository professors,
            ProfessorModuleService modules, ModuleGenerationState generationState, AiGenerationService generation,
            AssessmentService assessments, ActivityLogService audit, ModuleMaterialIndex index, PlatformTransactionManager manager) {
        this.sessions=sessions; this.professors=professors; this.modules=modules; this.generationState=generationState;
        this.generation=generation; this.assessments=assessments; this.audit=audit; this.index=index;
        transactions=new TransactionTemplate(manager);
    }
    @FunctionalInterface public interface Work<T> { T apply(AdminActingSessionEntity session) throws Exception; }
    // Only server code supplies Work. Every HTTP endpoint must enter through this guard.
    public <T> T execute(Authentication authentication, String id, Work<T> work) {
        return transactions.execute(tx -> {
            var session=sessions.requireProfessor(authentication,id);
            professors.findByUserId(session.getTarget().getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND"));
            try { return work.apply(session); }
            catch (RuntimeException error) { throw error; }
            catch (Exception error) { throw new IllegalStateException("Professor operation failed",error); }
        });
    }
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public ResponseEntity<AiLearningModuleResponse> generate(Authentication authentication,String sessionId,Long moduleId) {
        var captured=execute(authentication,sessionId,s -> {
            modules.findModule(moduleId,s.getTarget());
            var started=generationState.begin(moduleId);
            audit.createActingLog(s,ActivityType.MODULE,"Started lesson generation for module ID "+moduleId);
            return started;
        });
        var generated=generation.generatePrepared(moduleId,captured);
        var response=execute(authentication,sessionId,s -> {
            modules.findModule(moduleId,s.getTarget());
            var saved=generationState.finish(moduleId,generated);
            audit.createActingLog(s,ActivityType.MODULE,"Finished lesson generation for module ID "+moduleId+" with status "+saved.getAiGenerationStatus());
            return ResponseEntity.ok(modules.convertToResponse(saved));
        });
        if (generated.getAiGenerationStatus()==AiGenerationStatus.FAILED) throw generation.generationFailed();
        return response;
    }
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public ResponseEntity<AiLearningModuleResponse> approve(Authentication authentication,String sessionId,Long moduleId) {
        var captured=execute(authentication,sessionId,s -> {
            var module=modules.findModule(moduleId,s.getTarget());
            modules.requireApprovalReady(module);
            return module;
        });
        // Material preparation and assessment AI run without a transaction or acting-session lock.
        index.getOrBuild(captured);
        var prepared=assessments.prepareAssessment(captured);
        return execute(authentication,sessionId,s -> {
            var current=modules.findModule(moduleId,s.getTarget());
            if (!Objects.equals(current.getVersion(),captured.getVersion()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"STALE_MODULE_OPERATION");
            modules.requireApprovalReady(current);
            current.setStatus(LessonStatus.APPROVED);
            assessments.persistGeneratedAssessment(current,prepared);
            audit.createActingLog(s,ActivityType.MODULE,"Approved AI lesson for module ID "+moduleId);
            return ResponseEntity.ok(modules.convertToResponse(current));
        });
    }
}
