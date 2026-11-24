package com.ptc.halo.controller;

import com.ptc.halo.service.*;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping({"/api/professor/ai-learning-modules/{id}/sources","/api/admin/acting/professor/ai-learning-modules/{id}/sources"})
@PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
public class LessonSourceController extends ProfessorErrorResponses {
    private final ProfessorModuleService modules;private final AiLearningModuleRepository repository;
    private final LessonRevisionService revisions;private final LessonSourceScope sources;private final UserRepository users;
    private final AdminProfessorModeService acting;private final TransactionTemplate tx;private final ActivityLogService audit;
    @org.springframework.beans.factory.annotation.Autowired private AiGenerationService generation;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private ObtlStructureRecognizer recognizer;
    public LessonSourceController(ProfessorModuleService modules,AiLearningModuleRepository repository,LessonRevisionService revisions,LessonSourceScope sources,UserRepository users,AdminProfessorModeService acting,PlatformTransactionManager manager,ActivityLogService audit){
        this.modules=modules;this.repository=repository;this.revisions=revisions;this.sources=sources;this.users=users;this.acting=acting;this.tx=new TransactionTemplate(manager);this.audit=audit;
    }
    @FunctionalInterface interface Work<T>{T run(UserEntity professor,AdminActingSessionEntity session)throws Exception;}
    private <T>T access(Authentication auth,HttpServletRequest request,Work<T> work){
        if(request.getRequestURI().startsWith("/api/admin/acting/")){
            if(auth==null||auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))throw new AccessDeniedException("Admin required");
            return acting.execute(auth,request.getHeader("X-Acting-Session"),s->work.run(s.getTarget(),s));
        }
        return tx.execute(status->{
            if(auth==null||request.getHeader("X-Acting-Session")!=null||auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_PROFESSOR")))throw new AccessDeniedException("Professor required");
            var p=com.ptc.halo.security.AuthenticatedAccount.find(users,auth).filter(u->u.getRole()==Role.PROFESSOR&&u.getStatus()==Status.ACTIVE).orElseThrow(()->new AccessDeniedException("Professor unavailable"));
            try{return work.run(p,null);}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("Source operation failed",e);}
        });
    }
    public record Request(Long version,Long revisionVersion,List<LessonSourceScope.Choice> choices,LessonSourceScope.Mode mode){}
    public record View(Long version,LessonSourceScope.Snapshot source,LessonSourceScope.Lesson lesson,boolean recognitionEnabled){}
    @PostMapping("/recognize/{fileId}")
    public List<ObtlStructureRecognizer.Suggestion> recognize(@PathVariable Long id,@PathVariable Long fileId,
            @RequestParam String snapshotId,@RequestParam(required=false) Long revisionVersion,Authentication auth,HttpServletRequest request){
        var document=access(auth,request,(p,a)->{
            var m=candidate(id,revisionVersion,p);
            if(recognizer==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND");
            if(m.getSourceScope()==null)throw staleSource();
            var snapshot=sources.read(m.getSourceScope());
            if(!id.equals(snapshot.moduleId())||!snapshotId.equals(snapshot.id()))throw staleSource();
            return snapshot.documents().stream().filter(d->fileId.equals(d.fileId())).findFirst().orElseThrow(LessonSourceController::staleSource);
        });
        return recognizer.suggest(document);
    }
    @GetMapping(value="/original/{fileId}",produces="application/pdf")
    public org.springframework.http.ResponseEntity<byte[]> original(@PathVariable Long id,@PathVariable Long fileId,
            @RequestParam(required=false) Long revisionVersion,Authentication auth,HttpServletRequest request){
        byte[] bytes=access(auth,request,(p,a)->sources.originalForReview(candidate(id,revisionVersion,p),fileId));
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(bytes);
    }
    public record ReviewRequest(Long version,Long revisionVersion,LessonSourceScope.Lesson lesson,boolean acknowledged){}
    private AiLearningModuleEntity candidate(Long id,Long revisionVersion,UserEntity p){
        var m=modules.findModule(id,p);
        if(revisionVersion!=null)return revisions.sourceCandidate(id,revisionVersion,p);
        m.getFiles().size();m.getWeek().getSubject().getSubjectName();return m;
    }
    @GetMapping public View get(@PathVariable Long id,@RequestParam(required=false) Long revisionVersion,Authentication auth,HttpServletRequest request){
        return access(auth,request,(p,a)->{var m=candidate(id,revisionVersion,p);return new View(revisionVersion==null?m.getVersion():revisionVersion,m.getSourceScope()==null?null:sources.read(m.getSourceScope()),LessonSourceScope.Lesson.of(m),recognizer!=null);});
    }
    private void save(Long id,Request command,String snapshot,UserEntity p,AdminActingSessionEntity a){
        modules.writableModule(id,p);
        if(command.revisionVersion()!=null){if(!Objects.equals(command.version(),command.revisionVersion()))throw staleSource();revisions.saveSource(id,command.revisionVersion(),snapshot,p,a);return;}
        var m=repository.findForMentorOpenById(id).orElseThrow();
        if(m.getStatus()==LessonStatus.APPROVED||!Objects.equals(m.getVersion(),command.version()))throw staleSource();
        m.setSourceScope(snapshot);m.setGeneratedObjectives(null);m.setGeneratedKnowledge(null);m.setGeneratedExamples(null);m.setGeneratedSummary(null);
        m.setAiGenerationStatus(AiGenerationStatus.PENDING);m.setGenerationToken(null);repository.saveAndFlush(m);
        if(a==null)audit.createLog(p,ActivityType.MODULE,"Changed source selection for module ID "+id);
        else audit.createProfessorLog(p,a,ActivityType.MODULE,"Changed source selection for module ID "+id);
    }
    @PostMapping("/extract") public void extract(@PathVariable Long id,@RequestBody Request command,Authentication auth,HttpServletRequest request){
        var m=access(auth,request,(p,a)->{var c=candidate(id,command.revisionVersion(),p);if(command.version()==null||command.revisionVersion()==null&&(c.getStatus()==LessonStatus.APPROVED||!command.version().equals(c.getVersion())))throw staleSource();return c;});
        String snapshot=sources.extract(m);access(auth,request,(p,a)->{save(id,command,snapshot,p,a);return null;});
    }
    @PostMapping("/confirm") public void confirm(@PathVariable Long id,@RequestBody Request command,Authentication auth,HttpServletRequest request){
        access(auth,request,(p,a)->{var m=candidate(id,command.revisionVersion(),p);String snapshot=sources.confirm(m,command.choices(),a==null?p.getId():a.getAdmin().getId(),command.mode());save(id,command,snapshot,p,a);return null;});
    }
    @PostMapping("/review") public void review(@PathVariable Long id,@RequestBody ReviewRequest command,Authentication auth,HttpServletRequest request){
        if(!command.acknowledged()||command.lesson()==null)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"EXPANSION_REVIEW_REQUIRED");
        var captured=access(auth,request,(p,a)->{
            var m=candidate(id,command.revisionVersion(),p);
            if(command.version()==null||!Objects.equals(command.version(),command.revisionVersion()==null?m.getVersion():command.revisionVersion())||command.revisionVersion()==null&&m.getStatus()==LessonStatus.APPROVED)throw staleSource();
            if(!sources.expansion(m)||!sources.read(m.getSourceScope()).instructionVerified())throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"EXPANSION_REVIEW_REQUIRED");
            // Never edit a persistence-context entity while the provider call runs.
            var copy=new AiLearningModuleEntity();copy.setId(m.getId());copy.setWeek(m.getWeek());copy.setStatus(m.getStatus());copy.setSourceScope(m.getSourceScope());copy.getFiles().addAll(m.getFiles());
            LessonSourceScope.Lesson.of(m).apply(copy);return copy;
        });
        command.lesson().apply(captured);generation.validateExpansion(captured);
        access(auth,request,(p,a)->{
            sources.acknowledgeExpansion(captured,a==null?p.getId():a.getAdmin().getId());
            if(command.revisionVersion()!=null)revisions.saveExpansionReview(id,command.version(),captured,p,a);
            else {
                modules.writableModule(id,p);var m=repository.findForMentorOpenById(id).orElseThrow();
                if(m.getStatus()==LessonStatus.APPROVED||!Objects.equals(m.getVersion(),command.version()))throw staleSource();
                command.lesson().apply(m);m.setSourceScope(captured.getSourceScope());m.setAiGenerationStatus(AiGenerationStatus.COMPLETED);m.setGenerationToken(UUID.randomUUID().toString());repository.saveAndFlush(m);
                if(a==null)audit.createLog(p,ActivityType.MODULE,"Acknowledged AI expansion accuracy for module ID "+id);
                else audit.createProfessorLog(p,a,ActivityType.MODULE,"Acknowledged AI expansion accuracy for module ID "+id);
            }
            return null;
        });
    }
    private static ResponseStatusException  staleSource(){return new ResponseStatusException(HttpStatus.CONFLICT,"STALE_MODULE_OPERATION");}
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public org.springframework.http.ResponseEntity<?> invalidSource(){return ProfessorErrorResponses.reply(400,"INVALID_REQUEST");}
}
