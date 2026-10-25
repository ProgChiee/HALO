package com.ptc.halo;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.dtoRequest.*;
import com.ptc.halo.dtoResponse.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.open-in-view=false"},showSql=false)
@Import({AdminProfessorPreviewService.class,AdminProfessorModeService.class,AdminActingSessionService.class,ProfessorAcademicService.class,
    ProfessorModuleService.class,ModuleGenerationState.class,ActivityLogService.class,AssessmentService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AdminProfessorModeTest {
 @Autowired AdminProfessorPreviewService preview;
 @Autowired AdminProfessorModeService mode;
 @Autowired AdminActingSessionService acting;
 @Autowired ProfessorAcademicService academic;
 @Autowired ProfessorModuleService modules;
 @Autowired UserRepository users;
 @Autowired StudentProfileRepository profiles;
 @Autowired ProfessorRepository professors;
 @Autowired SubjectRepository subjects;
 @Autowired WeekRepository weeks;
 @Autowired AiLearningModuleRepository moduleRepo;
 @Autowired AiLearningFileRepository files;
 @Autowired AssessmentRepository assessments;
 @Autowired AssessmentQuestionRepository questions;
 @Autowired AdminActingSessionRepository sessions;
 @Autowired ActivityLogRepository logs;
 @Autowired org.springframework.transaction.PlatformTransactionManager manager;
 @SpyBean ActivityLogService audit;
 @MockBean AiGenerationService generation;
 @MockBean FileUploadService storage;
 @MockBean AssessmentAiService assessmentAi;
 @MockBean BadgeService badges;
 @MockBean StudentLearningProgressionService progression;
 @MockBean com.ptc.halo.component.ModuleMaterialIndex index;
 Authentication auth;
 UserEntity admin,professor,other;
 String session;
 @BeforeEach void setup() {
  reset(org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit),generation,storage,assessmentAi,index);
  logs.deleteAll();sessions.deleteAll();questions.deleteAll();assessments.deleteAll();files.deleteAll();moduleRepo.deleteAll();weeks.deleteAll();subjects.deleteAll();professors.deleteAll();profiles.deleteAll();users.deleteAll();
  admin=user("admin",Role.ADMIN);professor=user("prof",Role.PROFESSOR);other=user("other",Role.PROFESSOR);
  for(var u:List.of(professor,other)){var p=new ProfessorEntity();p.setUser(u);p.setProfessorId("P"+u.getId());professors.saveAndFlush(p);}
  auth=auth(admin);session=acting.create(auth,new AdminActingSessionRequest(professor.getId(),Role.PROFESSOR)).id();
 }
 UserEntity user(String name,Role role){var u=new UserEntity();u.setName(name);u.setEmail(name+"@example.test");u.setRole(role);u.setStatus(Status.ACTIVE);return users.saveAndFlush(u);}
 Authentication auth(UserEntity u){return TestIdentity.auth(u);}
 SubjectRequest subjectRequest(String code){var r=new SubjectRequest();r.setSubjectCode(code);r.setSubjectName(code);r.setDescription("Description");r.setYearLevel(YearLevel.FIRST_YEAR);return r;}
 Long subject(){return mode.execute(auth,session,s->academic.createSubject(subjectRequest("S"),s.getTarget(),s)).getId();}
 Long week(Long id){var r=new WeekRequest();r.setWeekNumber(1);r.setTitle("Week");return mode.execute(auth,session,s->academic.createWeek(id,r,s.getTarget(),s)).getId();}
 Long module(){return mode.execute(auth,session,s->modules.createModule(week(subject()),"Lesson",null,null,List.of(),s.getTarget(),s)).getBody().getId();}
 void tx(java.util.function.Consumer<org.springframework.transaction.TransactionStatus> f){new TransactionTemplate(manager).executeWithoutResult(f);}
 void ready(Long id){tx(t->{var m=moduleRepo.findById(id).orElseThrow();m.setAiGenerationStatus(AiGenerationStatus.COMPLETED);m.setGeneratedKnowledge("Knowledge");var file=new AiLearningFileEntity();file.setOriginalFileName("lesson.pdf");file.setStoredFileName("opaque.pdf");file.setFilePath("opaque.pdf");file.setFileType("application/pdf");m.addFile(file);moduleRepo.saveAndFlush(m);});}
 GeneratedAssessment generated(){var result=new GeneratedAssessment();var list=new ArrayList<GeneratedAssessmentQuestion>();for(int n=1;n<=10;n++){var q=new GeneratedAssessmentQuestion();q.setQuestionNumber(n);q.setQuestion("Question "+n+"?");q.setOptionA("A");q.setOptionB("B");q.setOptionC("C");q.setOptionD("D");q.setCorrectAnswer("A");list.add(q);}result.setQuestions(list);return result;}
 @Test void previewRechecksTokenVersionsExpiryAndUsesReadOnlyTransactions() {
  var p=preview.create(auth,professor.getId());
  assertEquals(Boolean.TRUE,preview.read(auth,p.id(),s->org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()));
  professor.setTokenVersion(professor.getTokenVersion()+1);users.saveAndFlush(professor);
  assertThrows(AdminApiException.class,()->preview.validate(auth,p.id()));
  var expired=new AdminActingSessionEntity(java.util.UUID.randomUUID().toString(),admin,professor,java.time.Instant.now().minusSeconds(100),java.time.Instant.now().minusSeconds(1));
  assertThrows(AdminApiException.class,()->acting.validateProfessorPreview(auth,expired));
  var student=user("preview-student",Role.STUDENT);
  var wrong=new AdminActingSessionEntity(java.util.UUID.randomUUID().toString(),admin,student,java.time.Instant.now(),java.time.Instant.now().plusSeconds(100));
  assertThrows(AdminApiException.class,()->acting.validateProfessorPreview(auth,wrong));
 }
 @Test void previewIsSeparateReadOnlyScopedAndRevocable() {
  Long moduleId=module();long stored=sessions.count(),academicCount=moduleRepo.count();
  var p=preview.create(auth,professor.getId());assertEquals(stored,sessions.count());
  assertEquals(professor.getId(),preview.validate(auth,p.id()).target().userId());
  assertNotNull(preview.read(auth,p.id(),s->modules.getModuleById(moduleId,s.getTarget(),null)));
  assertThrows(AdminApiException.class,()->mode.execute(auth,p.id(),s->academic.createSubject(subjectRequest("NO"),s.getTarget(),s)));
  var otherPreview=preview.create(auth,other.getId());
  assertThrows(ResponseStatusException.class,()->preview.read(auth,otherPreview.id(),s->modules.getModuleById(moduleId,s.getTarget(),null)));
  var another=user("another",Role.ADMIN);assertThrows(AdminApiException.class,()->preview.validate(auth(another),p.id()));
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->preview.create(auth(professor),professor.getId()));
  assertThrows(AdminApiException.class,()->preview.create(auth,admin.getId()));
  professor.setStatus(Status.ARCHIVED);users.saveAndFlush(professor);
  assertThrows(AdminApiException.class,()->preview.validate(auth,p.id()));assertThrows(AdminApiException.class,()->preview.create(auth,professor.getId()));
  professor.setStatus(Status.ACTIVE);users.saveAndFlush(professor);
  preview.revoke(auth,p.id());assertThrows(AdminApiException.class,()->preview.validate(auth,p.id()));
  assertEquals(academicCount,moduleRepo.count());
  tx(t->{var logsForPreview=logs.findAll().stream().filter(l->l.getAction().contains("preview")).toList();assertFalse(logsForPreview.isEmpty());for(var l:logsForPreview){assertEquals(admin.getId(),l.getUser().getId());assertEquals(Role.PROFESSOR,l.getActingTargetRole());assertNull(l.getActingSession());}});
 }
 @Test void createsTargetOwnedAcademicDataAndAdminAudits() {
  Long id=module();
  tx(t->{var m=moduleRepo.findById(id).orElseThrow();assertEquals(professor.getId(),m.getWeek().getSubject().getProfessor().getUser().getId());
   assertEquals(4,logs.count());for(var l:logs.findAll()){assertEquals(admin.getId(),l.getUser().getId());assertEquals(professor.getId(),l.getActingTarget().getId());assertEquals(session,l.getActingSession().getId());}});
 }
 @Test void allResourceMutationsAndReadsRejectForeignOwnershipBeforeSideEffects() {
  Long id=module();var otherAdmin=user("another-admin",Role.ADMIN);
  var foreign=acting.create(auth(otherAdmin),new AdminActingSessionRequest(other.getId(),Role.PROFESSOR)).id();
  long count=logs.count();
  var update=new AiLearningModuleUpdateRequest();update.setLessonText("Changed");
  for(var work:List.<AdminProfessorModeService.Work<?>>of(s->modules.getModuleById(id,s.getTarget(),s),
    s->modules.updateModule(id,update,s.getTarget(),s),s->modules.declineLesson(id,s.getTarget(),s),
    s->modules.uploadFile(id,new org.springframework.mock.web.MockMultipartFile("file",new byte[]{1}),s.getTarget(),s))) {
   var error=assertThrows(ResponseStatusException.class,()->mode.execute(auth(otherAdmin),foreign,work));assertEquals(404,error.getStatusCode().value());
  }
  assertEquals(404,assertThrows(ResponseStatusException.class,()->mode.generate(auth(otherAdmin),foreign,id)).getStatusCode().value());
  assertEquals(404,assertThrows(ResponseStatusException.class,()->mode.approve(auth(otherAdmin),foreign,id)).getStatusCode().value());
  var ids=new TransactionTemplate(manager).execute(t->{var m=moduleRepo.findById(id).orElseThrow();return List.of(m.getWeek().getSubject().getId(),m.getWeek().getId());});
  assertThrows(ResponseStatusException.class,()->mode.execute(auth(otherAdmin),foreign,s->{academic.deleteSubject(ids.get(0),s.getTarget(),s);return null;}));
  assertThrows(ResponseStatusException.class,()->mode.execute(auth(otherAdmin),foreign,s->{academic.deleteWeek(ids.get(1),s.getTarget(),s);return null;}));
  assertEquals(count,logs.count());verifyNoInteractions(generation,assessmentAi,index);
 }
 @Test void invalidActingSessionsCannotExecuteWork() {
  var student=user("student",Role.STUDENT);var profile=new StudentProfileEntity();profile.setUser(student);profile.setStudentId("29AHRT-0168");profile.setSection("BSHM 1A");profile.setYearLevel(YearLevel.FIRST_YEAR);profiles.saveAndFlush(profile);String wrong=acting.create(auth,new AdminActingSessionRequest(student.getId(),Role.STUDENT)).id();
  assertEquals(AdminApiException.Code.INVALID_TARGET_ROLE,assertThrows(AdminApiException.class,()->mode.execute(auth,wrong,s->true)).code);
  var a=user("other-admin",Role.ADMIN);assertThrows(AdminApiException.class,()->mode.execute(auth(a),session,s->true));
  acting.revoke(auth,session);assertThrows(AdminApiException.class,()->mode.execute(auth,session,s->true));
 }
 @Test void auditFailureRollsBackAcademicMutation() {
  ActivityLogService spy=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
  doThrow(new IllegalStateException("audit failure")).when(spy).createActingLog(any(),eq(ActivityType.MODULE),anyString());
  assertThrows(IllegalStateException.class,()->subject());assertEquals(0,subjects.count());assertEquals(1,logs.count());
 }
 @Test void normalGenerationUsesNoExternalTransactionAndPersistsTargetResult() {
  Long id=module();when(generation.generatePrepared(eq(id),any())).thenAnswer(call->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());var m=(AiLearningModuleEntity)call.getArgument(1);m.setAiGenerationStatus(AiGenerationStatus.COMPLETED);m.setGeneratedKnowledge("Result");return m;});
  assertEquals(AiGenerationStatus.COMPLETED,mode.generate(auth,session,id).getBody().getAiGenerationStatus());
  assertEquals("Result",moduleRepo.findById(id).orElseThrow().getGeneratedKnowledge());
 }
 @Test void revokedDuringGenerationCannotWriteFinalResult() {
  Long id=module();when(generation.generatePrepared(eq(id),any())).thenAnswer(call->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());acting.revoke(auth,session);var m=(AiLearningModuleEntity)call.getArgument(1);m.setAiGenerationStatus(AiGenerationStatus.COMPLETED);m.setGeneratedKnowledge("Stale");return m;});
  assertThrows(AdminApiException.class,()->mode.generate(auth,session,id));assertNull(moduleRepo.findById(id).orElseThrow().getGeneratedKnowledge());
 }
 @Test void materialUpdateDuringGenerationRejectsStaleResult() {
  Long id=module();when(generation.generatePrepared(eq(id),any())).thenAnswer(call->{var update=new AiLearningModuleUpdateRequest();update.setLessonText("New material");mode.execute(auth,session,s->modules.updateModule(id,update,s.getTarget(),s));var m=(AiLearningModuleEntity)call.getArgument(1);m.setAiGenerationStatus(AiGenerationStatus.COMPLETED);return m;});
  assertEquals(409,assertThrows(ResponseStatusException.class,()->mode.generate(auth,session,id)).getStatusCode().value());assertEquals("New material",moduleRepo.findById(id).orElseThrow().getLessonText());
 }
 @Test void publicationRequiresMaterialsAndCommitsAssessmentWithAudit() {
  Long id=module();assertThrows(ResponseStatusException.class,()->mode.approve(auth,session,id));ready(id);
  when(index.getOrBuild(any())).thenAnswer(call->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());return null;});
  when(assessmentAi.generateAssessment(any(),any(),any(),any(),any())).thenAnswer(call->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());return generated();});
  mode.approve(auth,session,id);
  assertEquals(LessonStatus.APPROVED,moduleRepo.findById(id).orElseThrow().getStatus());assertTrue(assessments.findByModuleId(id).isPresent());assertEquals(10,questions.count());
 }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
 void invalidInitialQuizCannotPublishLessonOrPersistQuestions(boolean actingMode){
  Long id=module();ready(id);var invalid=generated();invalid.getQuestions().get(9).setCorrectAnswer("Z");
  when(assessmentAi.generateAssessment(any(),any(),any(),any(),any())).thenReturn(invalid);
  var error=assertThrows(ResponseStatusException.class,()->{if(actingMode)mode.approve(auth,session,id);else modules.approveLesson(id,professor,null);});
  assertEquals("QUIZ_GENERATION_INVALID",error.getReason());assertEquals(LessonStatus.PENDING,moduleRepo.findById(id).orElseThrow().getStatus());assertEquals(0,assessments.count());assertEquals(0,questions.count());
 }
 @Test void revokedDuringAssessmentCannotPublishOrPersistQuestions() {
  Long id=module();ready(id);when(assessmentAi.generateAssessment(any(),any(),any(),any(),any())).thenAnswer(call->{acting.revoke(auth,session);return generated();});
  assertThrows(AdminApiException.class,()->mode.approve(auth,session,id));assertEquals(LessonStatus.PENDING,moduleRepo.findById(id).orElseThrow().getStatus());assertEquals(0,assessments.count());assertEquals(0,questions.count());
 }
 @Test void publicationAuditFailureRollsBackModuleAndAssessment() {
  Long id=module();ready(id);when(assessmentAi.generateAssessment(any(),any(),any(),any(),any())).thenReturn(generated());
  ActivityLogService spy=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
  doThrow(new IllegalStateException("audit failure")).when(spy).createActingLog(any(),eq(ActivityType.MODULE),startsWith("Approved"));
  assertThrows(IllegalStateException.class,()->mode.approve(auth,session,id));assertEquals(LessonStatus.PENDING,moduleRepo.findById(id).orElseThrow().getStatus());assertEquals(0,assessments.count());assertEquals(0,questions.count());
 }

 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 @Test void expiryDuringExternalWorkPreventsPublication() {
  Long id=module();ready(id);
  when(index.getOrBuild(any())).thenAnswer(call->{
   assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
   jdbc.update("update admin_acting_session set expires_at = ? where id = ?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),session);return null;
  });
  when(assessmentAi.generateAssessment(any(),any(),any(),any(),any())).thenReturn(generated());
  assertEquals(AdminApiException.Code.ACTING_SESSION_INVALID,assertThrows(AdminApiException.class,()->mode.approve(auth,session,id)).code);
  assertEquals(LessonStatus.PENDING,moduleRepo.findById(id).orElseThrow().getStatus());assertEquals(0,questions.count());
 }
 @Test void changedMaterialsDuringAssessmentRejectPublication() {
  Long id=module();ready(id);when(assessmentAi.generateAssessment(any(),any(),any(),any(),any())).thenAnswer(call->{
   var request=new AiLearningModuleUpdateRequest();request.setLessonText("Changed during assessment");
   mode.execute(auth,session,s->modules.updateModule(id,request,s.getTarget(),s));return generated();
  });
  assertEquals("STALE_MODULE_OPERATION",assertThrows(ResponseStatusException.class,()->mode.approve(auth,session,id)).getReason());
  assertEquals(0,assessments.count());assertEquals("Changed during assessment",moduleRepo.findById(id).orElseThrow().getLessonText());
 }
 @Test void generationFinalAuditFailureRollsBackResult() {
  Long id=module();when(generation.generatePrepared(eq(id),any())).thenAnswer(call->{var m=(AiLearningModuleEntity)call.getArgument(1);m.setGeneratedKnowledge("Result");m.setAiGenerationStatus(AiGenerationStatus.COMPLETED);return m;});
  ActivityLogService spy=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
  doThrow(new IllegalStateException("audit failure")).when(spy).createActingLog(any(),eq(ActivityType.MODULE),startsWith("Finished"));
  assertThrows(IllegalStateException.class,()->mode.generate(auth,session,id));assertNull(moduleRepo.findById(id).orElseThrow().getGeneratedKnowledge());
  assertEquals(AiGenerationStatus.PENDING,moduleRepo.findById(id).orElseThrow().getAiGenerationStatus());
 }
 @Test void noFilesCannotPublishEvenWithCompletedGeneration() {
  Long id=module();tx(t->{var m=moduleRepo.findById(id).orElseThrow();m.setAiGenerationStatus(AiGenerationStatus.COMPLETED);});
  assertEquals("MODULE_MATERIALS_REQUIRED",assertThrows(ResponseStatusException.class,()->mode.approve(auth,session,id)).getReason());verifyNoInteractions(index,assessmentAi);
 }
}
