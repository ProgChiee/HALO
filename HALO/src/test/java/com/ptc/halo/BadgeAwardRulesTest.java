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
class BadgeAwardRulesTest {
 @Autowired AssessmentService service;
 @Autowired BadgeService badgeService;
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
 boolean earned(BadgeType type){return badges.existsByStudentIdAndBadgeType(student.getId(),type);}
 void complete(AiLearningModuleEntity module){var p=new StudentModuleProgressEntity();p.setStudent(student);p.setModule(module);p.setCompleted(true);p.setCompletedAt(LocalDateTime.now());progress.saveAndFlush(p);}
 void check(){badgeService.checkAndAwardBadges(student,attempt);}
 @Test void openingOrStartingDoesNotAwardCompletionBadges(){
  check();assertFalse(earned(BadgeType.MODULE_FINISHER));assertFalse(earned(BadgeType.SUBJECT_MASTER));
 }
 @Test void passingExistingQuizAwardsNewAndExistingBadgesWithoutDuplicates(){
  service.submitAttempt(attempt.getId(),student,request(item(q1.getId(),"A"),item(q2.getId(),"B")));
  assertTrue(earned(BadgeType.MODULE_FINISHER));assertTrue(earned(BadgeType.SUBJECT_MASTER));assertTrue(earned(BadgeType.FIRST_STEP));assertTrue(earned(BadgeType.PERFECT_SCORE));
  long count=badges.count();check();assertEquals(count,badges.count());
 }
 @Test void allPublishedModulesMustBeCompletedButUnpublishedOnesDoNotCount(){
  var subject=subjects.findAll().get(0);
  var w=new WeekEntity();w.setSubject(subject);w.setWeekNumber(2);w.setTitle("Second");w=weeks.saveAndFlush(w);
  var m=new AiLearningModuleEntity();m.setWeek(w);m.setStatus(LessonStatus.APPROVED);m.setAiGenerationStatus(AiGenerationStatus.COMPLETED);m=modules.saveAndFlush(m);
  complete(modules.findAll().stream().min(java.util.Comparator.comparing(AiLearningModuleEntity::getId)).orElseThrow());
  check();assertTrue(earned(BadgeType.MODULE_FINISHER));assertFalse(earned(BadgeType.SUBJECT_MASTER));
  m.setStatus(LessonStatus.DECLINED);m=modules.saveAndFlush(m);check();assertTrue(earned(BadgeType.SUBJECT_MASTER));
 }
 @Test void zeroAvailableModulesAndWrongYearNeverQualify(){
  var m=modules.findAll().get(0);complete(m);
  m.setAiGenerationStatus(AiGenerationStatus.FAILED);m=modules.saveAndFlush(m);check();
  assertFalse(earned(BadgeType.MODULE_FINISHER));assertFalse(earned(BadgeType.SUBJECT_MASTER));
  m.setAiGenerationStatus(AiGenerationStatus.COMPLETED);modules.saveAndFlush(m);
  var profile=profiles.findByUserId(student.getId()).orElseThrow();profile.setYearLevel(YearLevel.SECOND_YEAR);profiles.saveAndFlush(profile);check();
  assertFalse(earned(BadgeType.MODULE_FINISHER));assertFalse(earned(BadgeType.SUBJECT_MASTER));
 }
 void seed(BadgeType type){var b=new StudentBadgeEntity();b.setStudent(student);b.setBadgeType(type);b.setBadgeName(type.name());b.setDescription("Fixture");b.setEarnedAt(LocalDateTime.now());badges.saveAndFlush(b);}
 @Test void achieverRequiresTenDistinctOrdinaryBadges(){
  var ordinary=Arrays.stream(BadgeType.values()).filter(t->t!=BadgeType.HALO_ACHIEVER).toList();
  assertEquals(11,ordinary.size());ordinary.subList(0,9).forEach(this::seed);check();assertFalse(earned(BadgeType.HALO_ACHIEVER));
  seed(ordinary.get(9));check();assertTrue(earned(BadgeType.HALO_ACHIEVER));long count=badges.count();check();assertEquals(count,badges.count());
 }
 @Test void concurrentAwardsCommitBothSurroundingOperations() throws Exception {
  complete(modules.findAll().get(0));
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  var ready=new java.util.concurrent.CountDownLatch(2);var go=new java.util.concurrent.CountDownLatch(1);
  java.util.concurrent.Callable<Boolean> operation=()->{
   ready.countDown();assertTrue(go.await(10,java.util.concurrent.TimeUnit.SECONDS));
   return new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(tx->{
    check();assertFalse(tx.isRollbackOnly());
    var marker=new SubjectEntity();marker.setSubjectCode(java.util.UUID.randomUUID().toString().substring(0,8));marker.setSubjectName("Committed operation");subjects.saveAndFlush(marker);
    return true;
   });
  };
  try {
   var first=pool.submit(operation);var second=pool.submit(operation);
   assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS));go.countDown();
   assertTrue(first.get(20,java.util.concurrent.TimeUnit.SECONDS));assertTrue(second.get(20,java.util.concurrent.TimeUnit.SECONDS));
  } finally {go.countDown();pool.shutdownNow();}
  assertEquals(3,subjects.count());
  assertEquals(1,badges.findAll().stream().filter(b->b.getBadgeType()==BadgeType.MODULE_FINISHER).count());
  assertEquals(1,badges.findAll().stream().filter(b->b.getBadgeType()==BadgeType.SUBJECT_MASTER).count());
  assertEquals(badges.count(),badges.findAll().stream().map(StudentBadgeEntity::getBadgeType).distinct().count());
  long count=badges.count();int logs=mockingDetails(audit).getInvocations().size();
  check();assertEquals(count,badges.count());assertEquals(logs,mockingDetails(audit).getInvocations().size());
 }
}