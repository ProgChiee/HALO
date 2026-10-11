package com.ptc.halo;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import com.ptc.halo.dtoRequest.StudentAnswerRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.open-in-view=false"},showSql=false)
@Import({AdminStudentPreviewService.class,StudentDashboardService.class,StudentSubjectService.class,StudentProgressService.class,StudentLessonService.class,ProfileService.class,AdminStudentModeService.class,AdminActingSessionService.class,ActivityLogService.class,MentorService.class,com.fasterxml.jackson.databind.ObjectMapper.class,AssessmentService.class,BadgeService.class,StudentLearningProgressionService.class,StudentLessonAccessService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AdminStudentPreviewTest {
 @Autowired AdminStudentPreviewService preview;
 @Autowired AssessmentService service;
 @Autowired MentorService mentor;
 @Autowired LessonStudyDayRepository studyDays;
 @Autowired MentorSessionRepository sessions;
 @Autowired MentorMessageRepository messages;
 @MockBean com.ptc.halo.component.ModuleMaterialIndex index;
 @MockBean(answer=org.mockito.Answers.RETURNS_DEEP_STUBS, reset=org.springframework.boot.test.mock.mockito.MockReset.NONE) org.springframework.ai.chat.client.ChatClient.Builder builder;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
 @Autowired UserRepository users;
 @Autowired SubjectRepository subjects;
 @Autowired WeekRepository weeks;
 @Autowired AiLearningModuleRepository modules;
 @Autowired AssessmentRepository assessments;
 @Autowired AssessmentQuestionRepository questions;
 @Autowired AssessmentAttemptRepository attempts;
 @Autowired StudentAnswerRepository answers;
 @Autowired StudentModuleProgressRepository progress;
 @Autowired StudentBadgeRepository badges;
 @MockBean AssessmentAiService ai;
 @Autowired StudentProfileRepository profiles;
 @org.springframework.boot.test.mock.mockito.SpyBean ActivityLogService audit;
 @Autowired StudentDashboardService dashboard;
 @Autowired StudentSubjectService subjectService;
 @Autowired StudentProgressService progressService;
 @Autowired StudentLessonService lessonService;
 @Autowired ProfileService profileService;
 @Autowired BadgeService badgeService;
 @Autowired AdminStudentModeService mode;
 @Autowired AdminActingSessionService acting;
 @Autowired AdminActingSessionRepository actingSessions;
 @Autowired ActivityLogRepository logs;
 @Autowired jakarta.persistence.EntityManager entityManager;
 UserEntity admin; String actingId; org.springframework.security.core.Authentication auth;
 UserEntity student;AssessmentAttemptEntity attempt;AssessmentQuestionEntity q1,q2,foreign;
 @BeforeEach void setup(){
  reset((ActivityLogService)org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit));logs.deleteAll();actingSessions.deleteAll();messages.deleteAll();sessions.deleteAll();badges.deleteAll();progress.deleteAll();answers.deleteAll();attempts.deleteAll();questions.deleteAll();assessments.deleteAll();modules.deleteAll();weeks.deleteAll();subjects.deleteAll();profiles.deleteAll();users.deleteAll();
  student=new UserEntity();student.setEmail("student@test.example");student.setName("Student");student.setRole(Role.STUDENT);student.setStatus(Status.ACTIVE);student=users.saveAndFlush(student);
  var subject=new SubjectEntity();subject.setYearLevel(YearLevel.FIRST_YEAR);subject.setSubjectCode("S");subject.setSubjectName("Subject");subject=subjects.saveAndFlush(subject);
  var week=new WeekEntity();week.setSubject(subject);week.setWeekNumber(1);week.setTitle("Week");week=weeks.saveAndFlush(week);
  var module=new AiLearningModuleEntity();module.setWeek(week);module.setAiGenerationStatus(AiGenerationStatus.COMPLETED);module.setStatus(LessonStatus.APPROVED);module=modules.saveAndFlush(module);
  var assessment=new AssessmentEntity();assessment.setModule(module);assessment.setTitle("Quiz");assessment.setPassingScore(70);assessment.setStatus(AssessmentStatus.AVAILABLE);assessment.setCreatedAt(LocalDateTime.now());assessment=assessments.saveAndFlush(assessment);
  q1=question(assessment,1,"A");q2=question(assessment,2,"B");
  var profile=new StudentProfileEntity();profile.setUser(student);profile.setStudentId("ST03-1");profile.setYearLevel(YearLevel.FIRST_YEAR);profiles.saveAndFlush(profile);

  admin=new UserEntity();admin.setEmail("admin@test.example");admin.setName("Admin");admin.setRole(Role.ADMIN);admin.setStatus(Status.ACTIVE);admin=users.saveAndFlush(admin);
  auth=authentication(admin);
  actingId=preview.create(auth,student.getId()).id();
 }
 AssessmentQuestionEntity question(AssessmentEntity assessment,int number,String correct){
  var q=new AssessmentQuestionEntity();q.setAssessment(assessment);q.setQuestionNumber(number);q.setQuestionText("Question");q.setOptionA("A");q.setOptionB("B");q.setOptionC("C");q.setOptionD("D");q.setCorrectAnswer(correct);return questions.saveAndFlush(q);
 }
 StudentAnswerRequest.AnswerItem item(Long id,String answer){var i=new StudentAnswerRequest.AnswerItem();i.setQuestionId(id);i.setAnswer(answer);return i;}
 StudentAnswerRequest request(StudentAnswerRequest.AnswerItem...items){var r=new StudentAnswerRequest();r.setAnswers(Arrays.asList(items));return r;}
 boolean active(){return org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive();}
 void prepareIndex(){
  when(index.getOrBuild(any())).thenAnswer(inv->{
   assertFalse(active(), "Indexing must run outside a database transaction");
   var m=(AiLearningModuleEntity)inv.getArgument(0);m.getFiles().size();
   return new com.ptc.halo.component.ModuleMaterialIndex.Index(m.getId(),"fixture",List.of());
  });
 }
 void respond(Runnable duringAi){
  var response=builder.build().prompt().system(anyString()).user(anyString()).call();
  doAnswer(inv->{
   assertFalse(active(),"AI must run outside a database transaction");duringAi.run();return "{\"inScope\":false,\"chunkIds\":[]}";
  }).when(response).content();
 }

 org.springframework.security.core.Authentication authentication(UserEntity user) {
  return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user.getEmail(),"unused",
   List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+user.getRole())));
 }
 Long moduleId(){return modules.findAll().get(0).getId();}

 void noHistory(){
  assertEquals(0,studyDays.count());assertEquals(0,attempts.count());assertEquals(0,answers.count());assertEquals(0,progress.count());assertEquals(0,badges.count());
  assertEquals(0,sessions.count());assertEquals(0,messages.count());assertEquals(0,actingSessions.count());
 }
 @Test void quizEvaluatesOnlyInMemoryAndRepeatedSubmitReturnsSameResult(){
  var opened=preview.start(auth,actingId,moduleId());
  assertEquals(opened.getAttemptId(),preview.start(auth,actingId,moduleId()).getAttemptId());
  var request=request(item(q1.getId(),"A"),item(q2.getId(),"B"));
  var result=preview.submit(auth,actingId,opened.getAttemptId(),request);
  assertTrue(result.isPreview());assertEquals(100,result.getScore());assertEquals(2,result.getCorrectCount());
  assertEquals(2,result.getFeedback().size());assertTrue(result.getPassed());
  assertEquals(result,preview.submit(auth,actingId,opened.getAttemptId(),request));
  assertEquals(result,preview.result(auth,actingId,opened.getAttemptId()));
  assertEquals(1,preview.history(auth,actingId,moduleId()).size());noHistory();
 }
 @Test void invalidAnswersAndRevokedEligibilityCannotGrade(){
  var opened=preview.start(auth,actingId,moduleId());
  assertEquals("INVALID_ANSWER_SET",assertThrows(ResponseStatusException.class,()->preview.submit(auth,actingId,opened.getAttemptId(),request(item(q1.getId(),"A"),item(q1.getId(),"A")))).getReason());
  var profile=profiles.findByUserId(student.getId()).orElseThrow();profile.setYearLevel(YearLevel.SECOND_YEAR);profiles.saveAndFlush(profile);
  assertThrows(ResponseStatusException.class,()->preview.submit(auth,actingId,opened.getAttemptId(),request(item(q1.getId(),"A"),item(q2.getId(),"B"))));noHistory();
 }
 @Test void assessmentChangesCannotGradeStaleQuestionSet(){
  var opened=preview.start(auth,actingId,moduleId());q1.setCorrectAnswer("C");questions.saveAndFlush(q1);
  assertEquals("PREVIEW_STATE_CHANGED",assertThrows(ResponseStatusException.class,()->preview.submit(auth,actingId,opened.getAttemptId(),request(item(q1.getId(),"A"),item(q2.getId(),"B")))).getReason());noHistory();
 }
 @Test void previewIdCannotAuthorizeRealSupportWritesAndRealSessionCannotAuthorizePreview(){
  assertThrows(AdminApiException.class,()->mode.execute(auth,actingId,s->service.startAttempt(moduleId(),s.getTarget(),s)));noHistory();
  var real=acting.create(auth,new com.ptc.halo.dtoRequest.AdminActingSessionRequest(student.getId(),Role.STUDENT)).id();
  assertThrows(AdminApiException.class,()->preview.start(auth,real,moduleId()));assertEquals(0,attempts.count());
 }
 @Test void sessionAuthorizationStillChecksActorRoleOwnerTargetStatusTokenAndRevocation(){
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->preview.create(authentication(student),student.getId()));
  var other=new UserEntity();other.setEmail("other-admin@test.example");other.setRole(Role.ADMIN);other.setStatus(Status.ACTIVE);other=users.saveAndFlush(other);
  var otherAuth=authentication(other);assertThrows(AdminApiException.class,()->preview.validate(otherAuth,actingId));
  assertThrows(AdminApiException.class,()->preview.create(auth,admin.getId()));
  assertThrows(AdminApiException.class,()->preview.create(auth,Long.MAX_VALUE));
  student.setStatus(Status.INACTIVE);users.saveAndFlush(student);assertThrows(AdminApiException.class,()->preview.validate(auth,actingId));assertThrows(AdminApiException.class,()->preview.create(auth,student.getId()));
  student.setStatus(Status.ACTIVE);users.saveAndFlush(student);
  preview.revoke(auth,actingId);assertThrows(AdminApiException.class,()->preview.validate(auth,actingId));noHistory();
 }
 @Test void realContextReadsAreScopedAndNoWritesOccur(){
  assertEquals(student.getId(),preview.read(auth,actingId,s->profileService.getStudentProfile(s)).getUserId());
  assertEquals(1,preview.read(auth,actingId,s->subjectService.getStudentSubjects(s)).size());
  assertTrue(preview.read(auth,actingId,s->progressService.getProgress(s)).isEmpty());
  assertTrue(preview.read(auth,actingId,s->badgeService.getStudentBadges(s)).isEmpty());noHistory();
 }
 @Test void temporaryMentorUsesExistingScopedAiCitationsAndIdempotency(){
  var chunk=new com.ptc.halo.component.ModuleMaterialIndex.Chunk("c1",1L,"Hospitality.pdf",2,"Hospitality topic","Service");
  when(index.getOrBuild(any())).thenAnswer(inv->{assertFalse(active());var m=(AiLearningModuleEntity)inv.getArgument(0);assertEquals(moduleId(),m.getId());return new com.ptc.halo.component.ModuleMaterialIndex.Index(m.getId(),"fixture",List.of(chunk));});
  var response=builder.build().prompt().system(anyString()).user(anyString()).call();
  var count=new java.util.concurrent.atomic.AtomicInteger();
  doAnswer(inv->{assertFalse(active());return count.getAndIncrement()%2==0 ? "{\"inScope\":true,\"chunkIds\":[\"c1\"]}" : "{\"inScope\":true,\"sourceIds\":[\"c1\"],\"answer\":\"A supported explanation.\"}";}).when(response).content();
  var chat=preview.open(auth,actingId,moduleId());assertTrue(chat.getMessages().isEmpty());
  String key=UUID.randomUUID().toString();var result=preview.send(auth,actingId,chat.getSessionId(),"Explain service",key);
  assertTrue(result.getHaloMessage().contains("Hospitality.pdf"));assertTrue(result.getHaloMessage().contains("page 2"));
  assertEquals(result,preview.send(auth,actingId,chat.getSessionId(),"Explain service",key));assertEquals(2,count.get());
  assertEquals(result,preview.exchange(auth,actingId,chat.getSessionId(),key));assertEquals(2,preview.conversation(auth,actingId,chat.getSessionId(),null).getMessages().size());noHistory();
 }
 @Test void revokedDuringIndexAndAiRejectTemporaryResult(){
  prepareIndex();var chat=preview.open(auth,actingId,moduleId());respond(()->preview.revoke(auth,actingId));
  assertThrows(AdminApiException.class,()->preview.send(auth,actingId,chat.getSessionId(),"Question",UUID.randomUUID().toString()));noHistory();
  actingId=preview.create(auth,student.getId()).id();
  doAnswer(inv->{assertFalse(active());preview.revoke(auth,actingId);return null;}).when(index).getOrBuild(any());
  assertThrows(AdminApiException.class,()->preview.open(auth,actingId,moduleId()));noHistory();
 }
 @Test void materialChangeDuringAiRejectsResponseWithoutHistory(){
  prepareIndex();var chat=preview.open(auth,actingId,moduleId());respond(()->{var m=modules.findById(moduleId()).orElseThrow();m.setGenerationToken("changed");modules.saveAndFlush(m);});
  assertEquals("MENTOR_STATE_CHANGED",assertThrows(ResponseStatusException.class,()->preview.send(auth,actingId,chat.getSessionId(),"Question",UUID.randomUUID().toString())).getReason());
  assertTrue(preview.conversation(auth,actingId,chat.getSessionId(),null).getMessages().isEmpty());noHistory();
 }
 @Test void separatePreviewSessionsCannotReadEachOthersTemporaryHistory(){
  prepareIndex();var chat=preview.open(auth,actingId,moduleId());var quiz=preview.start(auth,actingId,moduleId());var second=preview.create(auth,student.getId()).id();
  assertThrows(ResponseStatusException.class,()->preview.conversation(auth,second,chat.getSessionId(),null));
  assertThrows(ResponseStatusException.class,()->preview.result(auth,second,quiz.getAttemptId()));noHistory();
 }
 @Test void auditRecordsAdminAndTargetOnlyWithoutStudentContent(){
  preview.revoke(auth,actingId);
  new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx->{
   assertEquals(2,logs.count());for(var log:logs.findAll()){assertEquals(admin.getId(),log.getUser().getId());assertEquals(student.getId(),log.getActingTarget().getId());assertEquals(Role.STUDENT,log.getActingTargetRole());assertNull(log.getActingSession());assertTrue(log.getAction().contains("preview"));}
  });noHistory();
 }

 @Test void expiryAndTokenVersionChangesInvalidatePreview(){
  var context=acting.createStudentPreview(auth,student.getId());student.setTokenVersion(student.getTokenVersion()+1);users.saveAndFlush(student);
  assertThrows(AdminApiException.class,()->acting.validateStudentPreview(auth,context));
  assertThrows(AdminApiException.class,()->preview.validate(auth,actingId));
  var fresh=preview.create(auth,student.getId()).id();admin.setTokenVersion(admin.getTokenVersion()+1);users.saveAndFlush(admin);assertThrows(AdminApiException.class,()->preview.validate(auth,fresh));
  var expired=new AdminActingSessionEntity(UUID.randomUUID().toString(),admin,student,java.time.Instant.now().minusSeconds(60),java.time.Instant.now().minusSeconds(1));
  assertThrows(AdminApiException.class,()->acting.validateStudentPreview(auth,expired));noHistory();
 }
 @Test void failedPreviewAuditDoesNotCreateUsableSessionOrStudentHistory(){
  ActivityLogService spy=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
  doThrow(new IllegalStateException("audit failure")).when(spy).createPreviewLog(any(),any(),anyString());
  assertThrows(IllegalStateException.class,()->preview.create(auth,student.getId()));noHistory();
 }

 @Test void previewLeavesExistingRealScoresProgressBadgesAndMentorHistoryUnchanged(){
  var realAttempt=service.startAttempt(moduleId(),student);service.submitAttempt(realAttempt.getId(),student,request(item(q1.getId(),"A"),item(q2.getId(),"B")));
  prepareIndex();mentor.openSession(moduleId(),student);
  long badgeCount=badges.count(),auditCount=logs.count(),messageCount=messages.count();
  assertTrue(preview.status(auth,actingId,moduleId()).getCanTakeAssessment());
  var practice=preview.start(auth,actingId,moduleId());var result=preview.submit(auth,actingId,practice.getAttemptId(),request(item(q1.getId(),"D"),item(q2.getId(),"D")));
  assertEquals(0,result.getScore());assertEquals(100,attempts.findById(realAttempt.getId()).orElseThrow().getScore());
  var chat=preview.open(auth,actingId,moduleId());assertTrue(chat.getMessages().isEmpty());respond(()->{});preview.send(auth,actingId,chat.getSessionId(),"Question",UUID.randomUUID().toString());
  assertEquals(1,attempts.count());assertEquals(2,answers.count());assertEquals(1,progress.count());assertTrue(Boolean.TRUE.equals(progress.findAll().get(0).getCompleted()));
  assertEquals(badgeCount,badges.count());assertEquals(auditCount,logs.count());assertEquals(1,sessions.count());assertEquals(messageCount,messages.count());
 }
 @Test void previewRejectsForeignAnswersAndUnpublishedModule(){
  var quiz=preview.start(auth,actingId,moduleId());assertThrows(ResponseStatusException.class,()->preview.submit(auth,actingId,quiz.getAttemptId(),request(item(q1.getId(),"A"),item(Long.MAX_VALUE,"B"))));
  var module=modules.findById(moduleId()).orElseThrow();module.setStatus(LessonStatus.PENDING);modules.saveAndFlush(module);
  assertThrows(ResponseStatusException.class,()->preview.start(auth,actingId,moduleId()));assertThrows(ResponseStatusException.class,()->preview.submit(auth,actingId,quiz.getAttemptId(),request(item(q1.getId(),"A"),item(q2.getId(),"B"))));noHistory();
 }
 @Test void temporaryConversationHasStableBoundedOlderPages(){
  prepareIndex();respond(()->{});var chat=preview.open(auth,actingId,moduleId());
  for(int i=0;i<17;i++)preview.send(auth,actingId,chat.getSessionId(),"Question",UUID.randomUUID().toString());
  var recent=preview.conversation(auth,actingId,chat.getSessionId(),null);assertEquals(30,recent.getMessages().size());assertTrue(recent.isHasOlder());
  preview.send(auth,actingId,chat.getSessionId(),"New question",UUID.randomUUID().toString());
  var older=preview.conversation(auth,actingId,chat.getSessionId(),recent.getNextBeforeId());assertEquals(4,older.getMessages().size());assertFalse(older.isHasOlder());assertTrue(older.getMessages().get(3).getId()<recent.getMessages().get(0).getId());noHistory();
 }
 @Test void concurrentMentorRequestsCannotDuplicateTemporaryExchange() throws Exception {
  prepareIndex();var chat=preview.open(auth,actingId,moduleId());var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
  respond(()->{entered.countDown();try{assertTrue(release.await(10,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}});
  var pool=java.util.concurrent.Executors.newSingleThreadExecutor();String key=UUID.randomUUID().toString();
  try{
   var first=pool.submit(()->preview.send(auth,actingId,chat.getSessionId(),"Question",key));assertTrue(entered.await(10,java.util.concurrent.TimeUnit.SECONDS));
   assertEquals("PREVIEW_OPERATION_PENDING",assertThrows(ResponseStatusException.class,()->preview.send(auth,actingId,chat.getSessionId(),"Question",key)).getReason());
   release.countDown();var result=first.get(10,java.util.concurrent.TimeUnit.SECONDS);assertEquals(result,preview.send(auth,actingId,chat.getSessionId(),"Question",key));assertEquals(2,preview.conversation(auth,actingId,chat.getSessionId(),null).getMessages().size());noHistory();
  }finally{release.countDown();pool.shutdownNow();}
 }
}
