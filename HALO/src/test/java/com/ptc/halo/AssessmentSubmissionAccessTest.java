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
@Import({AssessmentService.class,BadgeService.class,StudentLearningProgressionService.class,StudentLessonAccessService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AssessmentSubmissionAccessTest {
 @Autowired AssessmentService service;
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
 @MockBean ActivityLogService audit;
 UserEntity student;AssessmentAttemptEntity attempt;AssessmentQuestionEntity q1,q2,foreign;
 @BeforeEach void setup(){
  reset(audit);badges.deleteAll();progress.deleteAll();answers.deleteAll();attempts.deleteAll();questions.deleteAll();assessments.deleteAll();modules.deleteAll();weeks.deleteAll();subjects.deleteAll();profiles.deleteAll();users.deleteAll();
  student=new UserEntity();student.setEmail("student@test.example");student.setName("Student");student.setRole(Role.STUDENT);student.setStatus(Status.ACTIVE);student=users.saveAndFlush(student);
  var subject=new SubjectEntity();subject.setYearLevel(YearLevel.FIRST_YEAR);subject.setSubjectCode("S");subject.setSubjectName("Subject");subject=subjects.saveAndFlush(subject);
  var week=new WeekEntity();week.setSubject(subject);week.setWeekNumber(1);week.setTitle("Week");week=weeks.saveAndFlush(week);
  var module=new AiLearningModuleEntity();module.setWeek(week);module.setAiGenerationStatus(AiGenerationStatus.COMPLETED);module.setStatus(LessonStatus.APPROVED);module=modules.saveAndFlush(module);
  var assessment=new AssessmentEntity();assessment.setModule(module);assessment.setTitle("Quiz");assessment.setPassingScore(70);assessment.setStatus(AssessmentStatus.AVAILABLE);assessment.setCreatedAt(LocalDateTime.now());assessment=assessments.saveAndFlush(assessment);
  q1=question(assessment,1,"A");q2=question(assessment,2,"B");
  var profile=new StudentProfileEntity();profile.setUser(student);profile.setStudentId("ST03-1");profile.setYearLevel(YearLevel.FIRST_YEAR);profiles.saveAndFlush(profile);
  attempt=service.startAttempt(module.getId(),student);
 }
 AssessmentQuestionEntity question(AssessmentEntity assessment,int number,String correct){
  var q=new AssessmentQuestionEntity();q.setAssessment(assessment);q.setQuestionNumber(number);q.setQuestionText("Question");q.setOptionA("A");q.setOptionB("B");q.setOptionC("C");q.setOptionD("D");q.setCorrectAnswer(correct);return questions.saveAndFlush(q);
 }
 StudentAnswerRequest.AnswerItem item(Long id,String answer){var i=new StudentAnswerRequest.AnswerItem();i.setQuestionId(id);i.setAnswer(answer);return i;}
 StudentAnswerRequest request(StudentAnswerRequest.AnswerItem...items){var r=new StudentAnswerRequest();r.setAnswers(Arrays.asList(items));return r;}
 void assertUnchanged(){
  assertEquals(0,answers.count());assertEquals(0,progress.count());assertEquals(0,badges.count());
  var stored=attempts.findById(attempt.getId()).orElseThrow();
  assertNull(stored.getSubmittedAt());assertEquals(0,stored.getScore());assertFalse(stored.getPassed());verifyNoInteractions(audit);
 }
 void denied(int status,String code){
  var error=assertThrows(ResponseStatusException.class,()->service.submitAttempt(attempt.getId(),student,request(item(q1.getId(),"A"),item(q2.getId(),"B"))));
  assertEquals(status,error.getStatusCode().value());assertEquals(code,error.getReason());assertUnchanged();
 }
 @Test void eligibleStartAndSubmitStillAwardsCredit(){
  var result=service.submitAttempt(attempt.getId(),student,request(item(q1.getId(),"A"),item(q2.getId(),"B")));
  assertEquals(100,result.getScore());assertTrue(result.getPassed());assertEquals(2,answers.count());assertEquals(1,progress.count());assertTrue(badges.count()>0);
 }
 @Test void yearLevelChangedAfterStartRejectsSubmission(){
  var profile=profiles.findByUserId(student.getId()).orElseThrow();profile.setYearLevel(YearLevel.SECOND_YEAR);profiles.saveAndFlush(profile);
  denied(403,"STUDENT_NOT_ENROLLED");
 }
 @Test void unpublishedModuleRejectsSubmission(){
  var module=modules.findAll().get(0);module.setStatus(LessonStatus.DECLINED);modules.saveAndFlush(module);
  denied(409,"LESSON_NOT_APPROVED");
 }
 @Test void generationNoLongerCompleteRejectsSubmission(){
  var module=modules.findAll().get(0);module.setAiGenerationStatus(AiGenerationStatus.PENDING);modules.saveAndFlush(module);
  denied(409,"LESSON_NOT_GENERATED");
 }
 @Test void inactiveAssessmentRejectsSubmission(){
  var assessment=assessments.findAll().get(0);assessment.setStatus(AssessmentStatus.INACTIVE);assessments.saveAndFlush(assessment);
  denied(409,"ASSESSMENT_NOT_AVAILABLE");
 }
 @Test void newlyRequiredPreviousWeekMustBeCompleted(){
  var week=weeks.findAll().get(0);week.setWeekNumber(2);weeks.saveAndFlush(week);
  var previous=new WeekEntity();previous.setSubject(subjects.findAll().get(0));previous.setWeekNumber(1);previous.setTitle("Previous");weeks.saveAndFlush(previous);
  denied(409,"PREVIOUS_WEEK_INCOMPLETE");
 }
 @Test void submittedResultMapsLazyFeedbackOutsideAnyCallerTransaction(){
  service.submitAttempt(attempt.getId(),student,request(item(q2.getId(),"C"),item(q1.getId(),"A")));
  assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
  var result=service.getAttemptResult(attempt.getId(),student);
  assertEquals(attempt.getId(),result.getAttemptId());assertEquals(50,result.getScore());assertFalse(result.getPassed());
  assertEquals(2,result.getFeedback().size());
  var first=result.getFeedback().get(0);var second=result.getFeedback().get(1);
  assertEquals(q1.getId(),first.getQuestionId());assertEquals(1,first.getQuestionNumber());
  assertEquals("Question",first.getQuestionText());assertEquals("A",first.getStudentAnswer());assertEquals("A",first.getCorrectAnswer());assertTrue(first.getCorrect());
  assertEquals(q2.getId(),second.getQuestionId());assertEquals("C",second.getStudentAnswer());assertEquals("B",second.getCorrectAnswer());assertFalse(second.getCorrect());
 }
 @Test void resultGuardsPreserveOwnershipAndSubmissionSecrecy(){
  var unfinished=assertThrows(RuntimeException.class,()->service.getAttemptResult(attempt.getId(),student));
  assertEquals("ASSESSMENT_NOT_SUBMITTED",((ResponseStatusException)unfinished).getReason());
  service.submitAttempt(attempt.getId(),student,request(item(q1.getId(),"A"),item(q2.getId(),"B")));
  var other=new UserEntity();other.setId(Long.MAX_VALUE);
  var denied=assertThrows(RuntimeException.class,()->service.getAttemptResult(attempt.getId(),other));
  assertEquals("ACCESS_DENIED",((ResponseStatusException)denied).getReason());
  var missing=assertThrows(RuntimeException.class,()->service.getAttemptResult(Long.MAX_VALUE,student));
  assertEquals("ATTEMPT_NOT_FOUND",((ResponseStatusException)missing).getReason());
 }
 @Test void missingResultReturnsSafe404() throws Exception {
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
    new com.ptc.halo.controller.StudentAssessmentController(service,users))
    .setControllerAdvice(new com.ptc.halo.controller.StudentLessonExceptionHandler()).build();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/student/assessment/result/"+Long.MAX_VALUE)
    .principal(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(student.getEmail(),"unused")))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound())
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("ATTEMPT_NOT_FOUND"))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value("ATTEMPT_NOT_FOUND"))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.trace").doesNotExist());
 }
 java.util.List<Object> concurrently(java.util.concurrent.Callable<?> action) throws Exception {
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  var ready=new java.util.concurrent.CountDownLatch(2);var go=new java.util.concurrent.CountDownLatch(1);
  java.util.concurrent.Callable<Object> task=()->{ready.countDown();assertTrue(go.await(10,java.util.concurrent.TimeUnit.SECONDS));
   try{return action.call();}catch(ResponseStatusException e){return e;}};
  try {
   var one=pool.submit(task);var two=pool.submit(task);
   assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS));go.countDown();
   return java.util.List.of(one.get(20,java.util.concurrent.TimeUnit.SECONDS),two.get(20,java.util.concurrent.TimeUnit.SECONDS));
  } finally {go.countDown();pool.shutdownNow();}
 }
 @Test void concurrentStartsCreateExactlyOneUnfinishedAttempt() throws Exception {
  Long moduleId=modules.findAll().get(0).getId();attempts.deleteAll();
  var results=concurrently(()->service.startAttempt(moduleId,student));
  var one=(AssessmentAttemptEntity)results.get(0);var two=(AssessmentAttemptEntity)results.get(1);
  assertEquals(one.getId(),two.getId());assertEquals(1,attempts.count());
  assertNull(attempts.findById(one.getId()).orElseThrow().getSubmittedAt());
 }
 @Test void concurrentSubmitsCommitOnceAndRepeatsHaveNoSideEffects() throws Exception {
  var results=concurrently(()->service.submitAttempt(attempt.getId(),student,request(item(q1.getId(),"A"),item(q2.getId(),"B"))));
  assertEquals(1,results.stream().filter(AssessmentAttemptEntity.class::isInstance).count());
  var error=(ResponseStatusException)results.stream().filter(ResponseStatusException.class::isInstance).findFirst().orElseThrow();
  assertEquals(409,error.getStatusCode().value());assertEquals("ASSESSMENT_ALREADY_SUBMITTED",error.getReason());
  assertEquals(2,answers.count());assertEquals(1,progress.count());assertEquals(100,attempts.findById(attempt.getId()).orElseThrow().getScore());
  var awarded=badges.findAll();assertFalse(awarded.isEmpty());
  assertEquals(awarded.size(),awarded.stream().map(StudentBadgeEntity::getBadgeType).distinct().count());
  int auditCount=mockingDetails(audit).getInvocations().size();
  assertEquals(2+awarded.size(),auditCount);
  var repeat=assertThrows(ResponseStatusException.class,()->service.submitAttempt(attempt.getId(),student,request(item(q1.getId(),"C"),item(q2.getId(),"C"))));
  assertEquals("ASSESSMENT_ALREADY_SUBMITTED",repeat.getReason());
  assertEquals(2,answers.count());assertEquals(1,progress.count());assertEquals(awarded.size(),badges.count());
  assertEquals(auditCount,mockingDetails(audit).getInvocations().size());
 }
 @Test void anotherStudentsAttemptRemainsInaccessible(){
  var other=new UserEntity();other.setId(Long.MAX_VALUE);
  assertThrows(RuntimeException.class,()->service.submitAttempt(attempt.getId(),other,request(item(q1.getId(),"A"),item(q2.getId(),"B"))));
  assertUnchanged();
 }
}
