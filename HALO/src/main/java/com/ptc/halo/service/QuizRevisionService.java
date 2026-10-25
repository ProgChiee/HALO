package com.ptc.halo.service;

import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.dtoResponse.*;
import com.ptc.halo.component.ModuleMaterialIndex;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

/** Authorization and short transactions are supplied by the Professor controller. */
@Service
public class QuizRevisionService {
 private final AssessmentRepository quizzes;
 private final AiLearningModuleRepository modules;
 private final ProfessorModuleService ownership;
 private final AssessmentService assessments;
 private final ModuleMaterialIndex index;
 private final ActivityLogService audit;
 private final LessonRevisionRepository revisions;
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
 public QuizRevisionService(AssessmentRepository quizzes,AiLearningModuleRepository modules,ProfessorModuleService ownership,AssessmentService assessments,ModuleMaterialIndex index,ActivityLogService audit,LessonRevisionRepository revisions){this.quizzes=quizzes;this.modules=modules;this.ownership=ownership;this.assessments=assessments;this.index=index;this.audit=audit;this.revisions=revisions;}
 public record Question(int number,String question,String optionA,String optionB,String optionC,String optionD,String correctAnswer){}
 public record Quiz(Long id,int version,Long lessonVersion,LocalDateTime generatedAt,LocalDateTime approvedAt,List<Question> questions){}
 public record View(boolean needsReview,Quiz current,Quiz draft){}
 public record Prepared(Long moduleVersion,Long currentId,int latestQuizVersion,AiLearningModuleEntity module){}
 private ResponseStatusException stale(){return new ResponseStatusException(HttpStatus.CONFLICT,"STALE_MODULE_OPERATION");}
 private AiLearningModuleEntity module(Long id,UserEntity professor,boolean lock){
  var m=ownership.findModule(id,professor);
  if(lock)m=modules.findForMentorOpenById(id).orElseThrow(this::stale);
  if(m.getStatus()!=LessonStatus.APPROVED)throw new ResponseStatusException(HttpStatus.CONFLICT,"REVISION_REQUIRES_PUBLISHED");
  m.getFiles().size(); return m;
 }
 private Quiz view(AssessmentEntity a){return new Quiz(a.getId(),a.getQuizVersion(),a.getSourceModuleVersion(),a.getCreatedAt(),a.getApprovedAt(),a.getQuestions().stream().map(q->new Question(q.getQuestionNumber(),q.getQuestionText(),q.getOptionA(),q.getOptionB(),q.getOptionC(),q.getOptionD(),q.getCorrectAnswer())).toList());}
 public View get(Long id,UserEntity p){var m=module(id,p,false);return new View(m.isAssessmentNeedsReview(),quizzes.findByModuleId(id).map(this::view).orElse(null),quizzes.findTopByModuleIdAndStatusOrderByQuizVersionDesc(id,AssessmentStatus.DRAFT).map(this::view).orElse(null));}
 public Prepared prepare(Long id,UserEntity p){var m=module(id,p,false);return new Prepared(m.getVersion(),quizzes.findByModuleId(id).map(AssessmentEntity::getId).orElse(null),quizzes.latestVersion(id),m);}
 public GeneratedAssessment generate(Prepared captured){
  // Only the published snapshot is indexed. Draft lesson revisions and previous questions are never inputs.
  index.getOrBuild(captured.module());
  try {var generated=assessments.prepareAssessment(captured.module());GeneratedAssessmentValidator.validate(generated);return generated;}
  catch(ResponseStatusException e){throw e;}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"QUIZ_GENERATION_FAILED");}
 }
 public View save(Long id,Prepared captured,GeneratedAssessment generated,UserEntity p,AdminActingSessionEntity acting){
  var m=module(id,p,true);
  if(!Objects.equals(m.getVersion(),captured.moduleVersion())||quizzes.latestVersion(id)!=captured.latestQuizVersion()||!Objects.equals(quizzes.findByModuleId(id).map(AssessmentEntity::getId).orElse(null),captured.currentId()))throw stale();
  GeneratedAssessmentValidator.validate(generated);
  var a=new AssessmentEntity();a.setModule(m);a.setTitle("Module Assessment");a.setPassingScore(quizzes.findByModuleId(id).map(AssessmentEntity::getPassingScore).orElse(70));a.setQuizVersion(captured.latestQuizVersion()+1);a.setSourceModuleVersion(m.getVersion());a.setStatus(AssessmentStatus.DRAFT);a.setCreatedAt(LocalDateTime.now());
  for(var q:generated.getQuestions()){var n=new AssessmentQuestionEntity();n.setQuestionNumber(q.getQuestionNumber());n.setQuestionText(q.getQuestion());n.setOptionA(q.getOptionA());n.setOptionB(q.getOptionB());n.setOptionC(q.getOptionC());n.setOptionD(q.getOptionD());n.setCorrectAnswer(q.getCorrectAnswer());a.addQuestion(n);}
  quizzes.findTopByModuleIdAndStatusOrderByQuizVersionDesc(id,AssessmentStatus.DRAFT).ifPresent(old->{old.setStatus(AssessmentStatus.RETIRED);quizzes.saveAndFlush(old);});
  quizzes.saveAndFlush(a);log(p,acting,"Generated quiz draft version "+a.getQuizVersion(),id);return get(id,p);
 }
 public View approve(Long id,Long draftId,UserEntity p,AdminActingSessionEntity acting){
  var m=module(id,p,true);var draft=quizzes.findTopByModuleIdAndStatusOrderByQuizVersionDesc(id,AssessmentStatus.DRAFT).orElseThrow(this::stale);
  if(!Objects.equals(draft.getId(),draftId)||!Objects.equals(draft.getSourceModuleVersion(),m.getVersion()))throw stale();
  Long previousVersion=m.getVersion();
  quizzes.findByModuleId(id).ifPresent(a->{a.setStatus(AssessmentStatus.RETIRED);quizzes.saveAndFlush(a);});
  draft.setStatus(AssessmentStatus.AVAILABLE);draft.setApprovedAt(LocalDateTime.now());quizzes.saveAndFlush(draft);
  m.setAssessmentNeedsReview(false);modules.saveAndFlush(m);
  // This locked transaction changes no lesson materials. Preserve compatible draft
  // baselines atomically with approval; genuine earlier lesson conflicts stay stale.
  if(!Objects.equals(previousVersion,m.getVersion())&&revisions.advanceBaselineAfterQuizApproval(id,previousVersion,m.getVersion())>0)
   entityManager.refresh(revisions.getReferenceById(id)); // synchronize any managed draft after the narrow bulk update
  log(p,acting,"Approved quiz version "+draft.getQuizVersion(),id);return get(id,p);
 }
 private void log(UserEntity p,AdminActingSessionEntity acting,String action,Long id){String text=action+" for module "+id;if(acting==null)audit.createLog(p,ActivityType.ASSESSMENT,text);else audit.createProfessorLog(p,acting,ActivityType.ASSESSMENT,text);}
}
