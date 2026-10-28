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
@Import({LessonStudyService.class,StudentDashboardService.class,StudentStudyActivityService.class,StudentSubjectService.class,StudentProgressService.class,StudentLessonService.class,ProfileService.class,AdminStudentModeService.class,AdminActingSessionService.class,ActivityLogService.class,MentorService.class,com.fasterxml.jackson.databind.ObjectMapper.class,AssessmentService.class,BadgeService.class,StudentLearningProgressionService.class,StudentLessonAccessService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AdminStudentModeTest {
 @Autowired AssessmentService service;
 @Autowired MentorService mentor;
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
 @Autowired StudentSubjectEnrollmentRepository enrollments;
 @Autowired StudentProfileRepository profiles;
 @org.springframework.boot.test.mock.mockito.SpyBean ActivityLogService audit;
 @Autowired StudentDashboardService dashboard;
 @Autowired StudentSubjectService subjectService;
 @Autowired StudentProgressService progressService;
 @Autowired StudentLessonService lessonService;
 @Autowired StudentLessonAccessService lessonAccess;
 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 @Autowired LessonStudyService study;
 @Autowired LessonStudyDayRepository studyDays;
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
  enrollments.deleteAll();
  reset((ActivityLogService)org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit));studyDays.deleteAll();logs.deleteAll();actingSessions.deleteAll();messages.deleteAll();sessions.deleteAll();badges.deleteAll();progress.deleteAll();answers.deleteAll();attempts.deleteAll();questions.deleteAll();assessments.deleteAll();modules.deleteAll();weeks.deleteAll();subjects.deleteAll();profiles.deleteAll();users.deleteAll();
  student=new UserEntity();student.setEmail("student@test.example");student.setName("Student");student.setRole(Role.STUDENT);student.setStatus(Status.ACTIVE);student=users.saveAndFlush(student);
  var subject=new SubjectEntity();subject.setYearLevel(YearLevel.FIRST_YEAR);subject.setSubjectCode("S");subject.setSubjectName("Subject");subject=subjects.saveAndFlush(subject);
  var week=new WeekEntity();week.setSubject(subject);week.setWeekNumber(1);week.setTitle("Week");week=weeks.saveAndFlush(week);
  var module=new AiLearningModuleEntity();module.setWeek(week);module.setAiGenerationStatus(AiGenerationStatus.COMPLETED);module.setStatus(LessonStatus.APPROVED);module=modules.saveAndFlush(module);
  var assessment=new AssessmentEntity();assessment.setModule(module);assessment.setTitle("Quiz");assessment.setPassingScore(70);assessment.setStatus(AssessmentStatus.AVAILABLE);assessment.setCreatedAt(LocalDateTime.now());assessment=assessments.saveAndFlush(assessment);
  q1=question(assessment,1,"A");q2=question(assessment,2,"B");
  var profile=new StudentProfileEntity();profile.setUser(student);profile.setSection("BSHM 1A");profile.setStudentId("ST03-1");profile.setYearLevel(YearLevel.FIRST_YEAR);profiles.saveAndFlush(profile);
  enrollments.saveAndFlush(new StudentSubjectEnrollmentEntity(student,subject));
  attempt=service.startAttempt(module.getId(),student);
  admin=new UserEntity();admin.setEmail("admin@test.example");admin.setName("Admin");admin.setRole(Role.ADMIN);admin.setStatus(Status.ACTIVE);admin=users.saveAndFlush(admin);
  auth=authentication(admin);
  actingId=acting.create(auth,new com.ptc.halo.dtoRequest.AdminActingSessionRequest(student.getId(),Role.STUDENT)).id();
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
  stubIntent();
 }

 org.springframework.security.core.Authentication authentication(UserEntity user) {
  return TestIdentity.auth(user);
 }
 Long moduleId(){return modules.findAll().get(0).getId();}
 AssessmentAttemptEntity submit(){return mode.execute(auth,actingId,s->service.submitAttempt(attempt.getId(),s.getTarget(),request(item(q1.getId(),"A"),item(q2.getId(),"B")),s));}
 void provenance(){new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx->{
  for(var log:logs.findAll()){assertEquals(admin.getId(),log.getUser().getId());assertEquals(student.getId(),log.getActingTarget().getId());assertEquals(Role.STUDENT,log.getActingTargetRole());assertEquals(actingId,log.getActingSession().getId());}
 });}
 @Test void quizCommitsToSelectedStudentWithAdminAuditAndRepeatIsSafe(){
  var saved=submit();assertEquals(100,saved.getScore());assertEquals(student.getId(),saved.getStudent().getId());
  assertEquals(2,answers.count());assertEquals(1,progress.count());assertTrue(badges.count()>0);provenance();
  long auditCount=logs.count(),badgeCount=badges.count();
  assertEquals("ASSESSMENT_ALREADY_SUBMITTED",assertThrows(ResponseStatusException.class,this::submit).getReason());
  assertEquals(2,answers.count());assertEquals(badgeCount,badges.count());assertEquals(auditCount,logs.count());
  var result=mode.execute(auth,actingId,s->service.getAttemptResult(saved.getId(),s.getTarget()));assertEquals(100,result.getScore());
 }
 @Test void auditFailureRollsBackAnswersScoreProgressAndBadges(){
  ActivityLogService spy=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
  doThrow(new IllegalStateException("test audit failure")).when(spy).createActingLog(any(),eq(ActivityType.BADGE),anyString());
  assertThrows(IllegalStateException.class,this::submit);
  assertEquals(0,answers.count());assertEquals(0,progress.count());assertEquals(0,badges.count());assertNull(attempts.findById(attempt.getId()).orElseThrow().getSubmittedAt());assertEquals(1,logs.count());
 }
 @Test void sessionGuardRejectsOtherAdminWrongRoleRevokedExpiredAndInactive(){
  var original=auth;
  auth=TestIdentity.auth(student);
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->mode.execute(auth,actingId,s->true));auth=original;
  var other=new UserEntity();other.setEmail("other-admin@test.example");other.setName("Other");other.setRole(Role.ADMIN);other.setStatus(Status.ACTIVE);other=users.saveAndFlush(other);
  var otherAuth=authentication(other);assertThrows(AdminApiException.class,()->mode.execute(otherAuth,actingId,s->true));
  var prof=new UserEntity();prof.setEmail("prof@test.example");prof.setRole(Role.PROFESSOR);prof.setStatus(Status.ACTIVE);prof=users.saveAndFlush(prof);
  var wrong=acting.create(auth,new com.ptc.halo.dtoRequest.AdminActingSessionRequest(prof.getId(),Role.PROFESSOR)).id();
  assertEquals(AdminApiException.Code.INVALID_TARGET_ROLE,assertThrows(AdminApiException.class,()->mode.execute(auth,wrong,s->true)).code);
  var expired=actingSessions.saveAndFlush(new AdminActingSessionEntity(UUID.randomUUID().toString(),admin,student,java.time.Instant.now().minusSeconds(100),java.time.Instant.now().minusSeconds(1)));
  assertThrows(AdminApiException.class,()->mode.execute(auth,expired.getId(),s->true));
  student.setStatus(Status.INACTIVE);users.saveAndFlush(student);assertThrows(AdminApiException.class,()->mode.execute(auth,actingId,s->true));student.setStatus(Status.ACTIVE);users.saveAndFlush(student);
  acting.revoke(auth,actingId);assertThrows(AdminApiException.class,()->mode.execute(auth,actingId,s->true));
 }
 @Test void differentStudentCannotUseAttemptOrMentorSession(){
  prepareIndex();var opened=mode.openMentor(auth,actingId,moduleId());
  var other=new UserEntity();other.setEmail("other-student@test.example");other.setName("Other");other.setRole(Role.STUDENT);other.setStatus(Status.ACTIVE);other=users.saveAndFlush(other);
  var profile=new StudentProfileEntity();profile.setUser(other);profile.setSection("BSHM 1A");profile.setStudentId("Other");profile.setYearLevel(YearLevel.FIRST_YEAR);profiles.saveAndFlush(profile);
  var otherId=acting.create(auth,new com.ptc.halo.dtoRequest.AdminActingSessionRequest(other.getId(),Role.STUDENT)).id();
  assertThrows(ResponseStatusException.class,()->mode.execute(auth,otherId,s->service.getAttemptResult(attempt.getId(),s.getTarget())));
  assertThrows(ResponseStatusException.class,()->mode.execute(auth,otherId,s->service.submitAttempt(attempt.getId(),s.getTarget(),request(),s)));
  assertThrows(ResponseStatusException.class,()->mode.sendMessage(auth,otherId,opened.getSessionId(),"Question",UUID.randomUUID().toString()));
  assertThrows(ResponseStatusException.class,()->mode.execute(auth,otherId,s->mentor.getConversation(opened.getSessionId(),s.getTarget())));
  assertEquals(0,answers.count());assertEquals(1,messages.count());
 }
 @Test void mentorUsesShortTransactionsAndIdempotentExchangeWithAdminProvenance(){
  prepareIndex();var opened=mode.openMentor(auth,actingId,moduleId());respond(()->{});
  String key=UUID.randomUUID().toString();var first=mode.sendMessage(auth,actingId,opened.getSessionId(),"Question",key);
  long count=logs.count();var repeated=mode.sendMessage(auth,actingId,opened.getSessionId(),"Question",key);
  assertEquals(first.getHaloMessage(),repeated.getHaloMessage());assertEquals(3,messages.count());assertEquals(count,logs.count());provenance();
  assertEquals(first.getHaloMessage(),mode.execute(auth,actingId,s->mentor.getExchange(opened.getSessionId(),s.getTarget(),key)).getHaloMessage());
  new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx->assertEquals(student.getId(),sessions.findById(opened.getSessionId()).orElseThrow().getStudent().getId()));
 }
 @Test void revokedDuringAiCannotWriteExchange(){
  prepareIndex();var opened=mode.openMentor(auth,actingId,moduleId());respond(()->acting.revoke(auth,actingId));
  assertThrows(AdminApiException.class,()->mode.sendMessage(auth,actingId,opened.getSessionId(),"Question",UUID.randomUUID().toString()));assertEquals(1,messages.count());
 }
 @Test void revokedDuringIndexCannotOpenSession(){
  when(index.getOrBuild(any())).thenAnswer(inv->{assertFalse(active());acting.revoke(auth,actingId);return null;});
  assertThrows(AdminApiException.class,()->mode.openMentor(auth,actingId,moduleId()));assertEquals(0,sessions.count());assertEquals(0,messages.count());
 }
 @Test void mentorAuditFailureRollsBackExchange(){
  prepareIndex();var opened=mode.openMentor(auth,actingId,moduleId());respond(()->{});
  ActivityLogService spy=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
  doThrow(new IllegalStateException("audit failed")).when(spy).createActingLog(any(),eq(ActivityType.MODULE),anyString());
  assertThrows(IllegalStateException.class,()->mode.sendMessage(auth,actingId,opened.getSessionId(),"Question",UUID.randomUUID().toString()));assertEquals(1,messages.count());
 }
 @Test void concurrentStartsReuseOneAttemptAndConcurrentSubmitsCommitOnce() throws Exception {
  attempts.deleteAll();logs.deleteAll();
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  try {
   var gate=new java.util.concurrent.CountDownLatch(1);
   var tasks=new ArrayList<java.util.concurrent.Future<Long>>();
   for(int i=0;i<2;i++)tasks.add(pool.submit(()->{gate.await();return mode.execute(auth,actingId,s->service.startAttempt(moduleId(),s.getTarget(),s)).getId();}));
   gate.countDown();Long id=tasks.get(0).get(15,java.util.concurrent.TimeUnit.SECONDS);assertEquals(id,tasks.get(1).get(15,java.util.concurrent.TimeUnit.SECONDS));assertEquals(1,attempts.count());assertEquals(1,logs.count());attempt=attempts.findById(id).orElseThrow();
   var submits=new ArrayList<java.util.concurrent.Future<String>>();
   for(int i=0;i<2;i++)submits.add(pool.submit(()->{try{submit();return "OK";}catch(ResponseStatusException e){return e.getReason();}}));
   var outcomes=new HashSet<String>();for(var f:submits)outcomes.add(f.get(15,java.util.concurrent.TimeUnit.SECONDS));
   assertEquals(Set.of("OK","ASSESSMENT_ALREADY_SUBMITTED"),outcomes);assertEquals(2,answers.count());assertEquals(1,progress.count());provenance();
  } finally {pool.shutdownNow();}
 }

 @Test void readsUseSelectedStudentAndExistingEligibility(){
  assertEquals(student.getId(),mode.execute(auth,actingId,s->profileService.getStudentProfile(s.getTarget())).getUserId());
  assertNotNull(mode.execute(auth,actingId,s->dashboard.getDashboard(s.getTarget())));
  assertEquals(1,mode.execute(auth,actingId,s->subjectService.getStudentSubjects(s.getTarget())).size());
  assertTrue(mode.execute(auth,actingId,s->progressService.getProgress(s.getTarget())).isEmpty());
  assertTrue(mode.execute(auth,actingId,s->badgeService.getStudentBadges(s.getTarget())).isEmpty());
  Long weekId=weeks.findAll().get(0).getId();
  assertEquals(moduleId(),mode.execute(auth,actingId,s->lessonService.byWeek(weekId,s.getTarget())).getId());
  submit();
  assertEquals(1,mode.execute(auth,actingId,s->progressService.getProgress(s.getTarget())).size());
  assertFalse(mode.execute(auth,actingId,s->badgeService.getStudentBadges(s.getTarget())).isEmpty());
  var profile=profiles.findByUserId(student.getId()).orElseThrow();profile.setYearLevel(YearLevel.SECOND_YEAR);profiles.saveAndFlush(profile);
  assertThrows(ResponseStatusException.class,()->mode.execute(auth,actingId,s->lessonService.byWeek(weekId,s.getTarget())));
 }
 @Test void expiryDuringAiAndMaterialChangeRejectFinalWrites(){
  prepareIndex();var opened=mode.openMentor(auth,actingId,moduleId());
  respond(()->new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx->{
   entityManager.createNativeQuery("update admin_acting_session set expires_at = :expiry where id = :id").setParameter("expiry",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1))).setParameter("id",actingId).executeUpdate();
  }));
  assertThrows(AdminApiException.class,()->mode.sendMessage(auth,actingId,opened.getSessionId(),"Question",UUID.randomUUID().toString()));assertEquals(1,messages.count());
  actingId=acting.create(auth,new com.ptc.halo.dtoRequest.AdminActingSessionRequest(student.getId(),Role.STUDENT)).id();
  respond(()->{var module=modules.findById(moduleId()).orElseThrow();module.setGenerationToken("updated");modules.saveAndFlush(module);});
  assertEquals("MENTOR_STATE_CHANGED",assertThrows(ResponseStatusException.class,()->mode.sendMessage(auth,actingId,opened.getSessionId(),"Question",UUID.randomUUID().toString())).getReason());assertEquals(1,messages.count());
 }
 @Test void actingConversationKeepsBoundedOlderHistory(){
  prepareIndex();var opened=mode.openMentor(auth,actingId,moduleId());var session=sessions.findById(opened.getSessionId()).orElseThrow();
  for(int i=0;i<35;i++){var message=new MentorMessageEntity();message.setSession(session);message.setSender(MessageSender.HALO);message.setMessage("History "+i);message.setCreatedAt(LocalDateTime.now());messages.saveAndFlush(message);}
  var page=mode.execute(auth,actingId,s->mentor.getConversation(opened.getSessionId(),s.getTarget()));
  assertEquals(30,page.getMessages().size());assertTrue(page.isHasOlder());
  var older=mode.execute(auth,actingId,s->mentor.getConversation(opened.getSessionId(),s.getTarget(),page.getNextBeforeId()));
  assertEquals(6,older.getMessages().size());assertFalse(older.isHasOlder());
  assertTrue(older.getMessages().get(5).getId()<page.getMessages().get(0).getId());
 }

 @Test void supportStudyTracksSelectedStudentAndAuditRollsBackTogether() throws Exception {
  Long weekId=weeks.findAll().get(0).getId();
  mode.execute(auth,actingId,s->{var id=study.record(weekId,s.getTarget());audit.createActingLog(s,ActivityType.MODULE,"Studied module ID "+id);return null;});
  assertTrue(studyDays.existsByStudentIdAndModuleIdAndStudyDate(student.getId(),moduleId(),java.time.LocalDate.now()));
  var activity=mode.execute(auth,actingId,session->dashboard.getDashboard(session.getTarget()).getStudyActivity());assertEquals(1,activity.weeklyStudyDays());assertEquals(1,activity.studyStreak());
  studyDays.deleteAll();
  var auditTarget=(ActivityLogService)org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
  doThrow(new IllegalStateException("Audit unavailable")).when(auditTarget).createActingLog(any(),eq(ActivityType.MODULE),eq("Study audit"));
  assertThrows(IllegalStateException.class,()->mode.execute(auth,actingId,s->{study.record(weekId,s.getTarget());audit.createActingLog(s,ActivityType.MODULE,"Study audit");return null;}));
  assertEquals(0,studyDays.count());
 }

 void stubIntent() {
  var spec=mock(org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec.class,org.mockito.Answers.RETURNS_DEEP_STUBS);
  when(builder.build().prompt().system(startsWith("Classify HALO"))).thenReturn(spec);
  when(spec.user(anyString())).thenReturn(spec);
  when(spec.call().content()).thenAnswer(inv->{assertFalse(active());return "{\"intent\":\"LESSON_QUESTION\"}";});
 }

 @Test void enrollmentIsRequiredForDirectLearningRequestsAndOldSessionIds() throws Exception {
  prepareIndex();long moduleId=moduleId(),weekId=weeks.findAll().get(0).getId();
  var chat=mentor.openSession(moduleId,student);
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
   new com.ptc.halo.controller.StudentAiLearningController(lessonService,lessonAccess),
   new com.ptc.halo.controller.StudentMentorController(mentor,lessonAccess),
   new com.ptc.halo.controller.StudentAssessmentController(service,users),
   new com.ptc.halo.controller.LessonStudyController(study,lessonAccess,mode,audit))
   .setControllerAdvice(new com.ptc.halo.controller.StudentLessonExceptionHandler()).build();
  var caller=authentication(student);
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/student/ai-learning-modules/week/"+weekId).principal(caller)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
  enrollments.deleteAll();var before=learningSnapshot();
  var getPaths=List.of("/api/student/ai-learning-modules/week/"+weekId,"/api/student/mentor/session/"+chat.getSessionId(),"/api/student/mentor/message/"+chat.getSessionId()+"/request/"+UUID.randomUUID(),"/api/student/assessment/"+moduleId,"/api/student/assessment/status/"+moduleId,"/api/student/assessment/result/"+attempt.getId());
  for(var path:getPaths)mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).principal(caller)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("ENROLLMENT_REQUIRED"));
  var postPaths=List.of("/api/student/mentor/open/"+moduleId,"/api/student/mentor/message/"+chat.getSessionId(),"/api/student/assessment/start/"+moduleId,"/api/student/assessment/submit/"+attempt.getId(),"/api/student/ai-learning-modules/week/"+weekId+"/study");
  for(var path:postPaths){
   String body=path.contains("/submit/")?new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request(item(q1.getId(),"A"),item(q2.getId(),"B"))):"{\"message\":\"Explain the lesson\"}";
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).principal(caller).contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("ENROLLMENT_REQUIRED"));
  }
  assertEquals("ENROLLMENT_REQUIRED",assertThrows(ResponseStatusException.class,()->mode.openMentor(auth,actingId,moduleId)).getReason());
  assertEquals("ENROLLMENT_REQUIRED",assertThrows(ResponseStatusException.class,()->mode.execute(auth,actingId,s->service.submitAttempt(attempt.getId(),s.getTarget(),request(item(q1.getId(),"A"),item(q2.getId(),"B")),s))).getReason());
  assertEquals(before,learningSnapshot());assertEquals(1,sessions.count());assertNull(attempts.findById(attempt.getId()).orElseThrow().getSubmittedAt());
 }
 List<?> learningSnapshot(){return List.of("assessment_attempts","student_answers","student_module_progress","mentor_sessions","mentor_messages","lesson_study_days","student_badges","activity_log_entity").stream().map(t->jdbc.queryForList("select * from "+t+" order by id")).toList();}
 @Test void lossOfEnrollmentDuringMentorGenerationCannotCommitNewMessages(){
  prepareIndex();var chat=mentor.openSession(moduleId(),student);var before=learningSnapshot();
  respond(()->enrollments.deleteAll());
  assertEquals("ENROLLMENT_REQUIRED",assertThrows(ResponseStatusException.class,()->mentor.sendMessage(chat.getSessionId(),student,"Explain this lesson",UUID.randomUUID().toString())).getReason());
  assertEquals(before,learningSnapshot());
 }

 @Test void enrollmentRestrictionDoesNotDeleteOrRegradeCompletedAssessmentHistory(){
  var completed=service.submitAttempt(attempt.getId(),student,request(item(q1.getId(),"A"),item(q2.getId(),"B")));
  assertEquals(100,completed.getScore());enrollments.deleteAll();var before=learningSnapshot();
  assertEquals("ENROLLMENT_REQUIRED",assertThrows(ResponseStatusException.class,()->service.getAttemptResult(completed.getId(),student)).getReason());
  assertEquals(before,learningSnapshot());assertEquals(100,attempts.findById(completed.getId()).orElseThrow().getScore());assertEquals(2,answers.count());assertEquals(1,progress.count());
 }
}
