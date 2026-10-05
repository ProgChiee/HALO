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

@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.open-in-view=false","spring.jpa.properties.hibernate.generate_statistics=true"},showSql=false)
@Import({StudentSubjectService.class,AssessmentService.class,BadgeService.class,StudentLearningProgressionService.class,StudentLessonAccessService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class StudentSubjectBulkTest {
 @Autowired AssessmentService service;
 @Autowired StudentSubjectService summaries;
 @Autowired StudentLearningProgressionService progression;
 @Autowired jakarta.persistence.EntityManagerFactory emf;
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
 void complete(AiLearningModuleEntity module){var p=new StudentModuleProgressEntity();p.setStudent(student);p.setModule(module);p.setCompleted(true);progress.saveAndFlush(p);}
 AiLearningModuleEntity addModule(int number,LessonStatus status,AiGenerationStatus generation){
  var w=new WeekEntity();w.setSubject(subjects.findAll().get(0));w.setWeekNumber(number);w.setTitle("Week "+number);w=weeks.saveAndFlush(w);
  var m=new AiLearningModuleEntity();m.setWeek(w);m.setStatus(status);m.setAiGenerationStatus(generation);return modules.saveAndFlush(m);
 }
 @Test void summariesAndProgressionUseConstantQueriesAsWeekCountGrows(){
  var stats=emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
  Long subjectId=subjects.findAll().get(0).getId();complete(modules.findAll().get(0));
  stats.clear();var initial=summaries.getStudentSubjects(student);long summaryQueries=stats.getPrepareStatementCount();
  assertEquals(1,initial.get(0).getCompletedWeeks());assertEquals(100,initial.get(0).getProgressPercentage());
  stats.clear();progression.getWeekAccess(subjectId,student);long accessQueries=stats.getPrepareStatementCount();
  for(int i=2;i<=12;i++)addModule(i,LessonStatus.APPROVED,AiGenerationStatus.COMPLETED);
  stats.clear();var result=summaries.getStudentSubjects(student);
  assertEquals(summaryQueries,stats.getPrepareStatementCount());assertTrue(summaryQueries<=5);
  assertEquals(12,result.get(0).getTotalWeeks());assertEquals(1,result.get(0).getCompletedWeeks());assertEquals(8,result.get(0).getProgressPercentage());
  stats.clear();var access=progression.getWeekAccess(subjectId,student);
  assertEquals(accessQueries,stats.getPrepareStatementCount());assertTrue(accessQueries<=5);
  assertTrue(access.get(0).getUnlocked());assertTrue(access.get(1).getUnlocked());assertFalse(access.get(2).getUnlocked());
 }
 @Test void unpublishedAndIncompleteGenerationStayHiddenAndDoNotUnlockNextWeek(){
  Long subjectId=subjects.findAll().get(0).getId();
  var declined=addModule(2,LessonStatus.DECLINED,AiGenerationStatus.COMPLETED);complete(declined);
  var failed=addModule(3,LessonStatus.APPROVED,AiGenerationStatus.FAILED);complete(failed);
  complete(modules.findAll().stream().min(java.util.Comparator.comparing(AiLearningModuleEntity::getId)).orElseThrow());
  var rows=progression.getWeekAccess(subjectId,student);
  assertNull(rows.get(1).getModuleId());assertFalse(rows.get(1).getLessonAvailable());assertFalse(rows.get(1).getCompleted());
  assertNull(rows.get(2).getModuleId());assertFalse(rows.get(2).getUnlocked());
  assertEquals(1,summaries.getStudentSubjects(student).get(0).getCompletedWeeks());
 }
 @Test void eligibleModuleDenominatorExcludesEmptyAndUnpublishedWeeks(){
  var subject=subjects.findAll().get(0);
  var empty=new WeekEntity();empty.setSubject(subject);empty.setWeekNumber(2);empty.setTitle("Empty");weeks.saveAndFlush(empty);
  addModule(3,LessonStatus.DECLINED,AiGenerationStatus.COMPLETED);
  addModule(4,LessonStatus.APPROVED,AiGenerationStatus.FAILED);
  var available=modules.findAll().stream().min(java.util.Comparator.comparing(AiLearningModuleEntity::getId)).orElseThrow();complete(available);
  var result=summaries.getStudentSubjects(student).get(0);
  assertEquals(4,result.getTotalWeeks());assertEquals(1,result.getEligibleModuleCount());assertEquals(1,result.getCompletedWeeks());assertEquals(100,result.getProgressPercentage());
  available.setStatus(LessonStatus.DECLINED);modules.saveAndFlush(available);
  result=summaries.getStudentSubjects(student).get(0);
  assertEquals(0,result.getEligibleModuleCount());assertEquals(0,result.getCompletedWeeks());assertEquals(0,result.getProgressPercentage());
 }
 @Test void eligibilityAndStudentIsolationRemainIntact(){
  Long subjectId=subjects.findAll().get(0).getId();complete(modules.findAll().get(0));
  var other=new UserEntity();other.setName("Other");other.setEmail("other@example.test");other.setRole(Role.STUDENT);other.setStatus(Status.ACTIVE);other=users.saveAndFlush(other);
  var profile=new StudentProfileEntity();profile.setUser(other);profile.setStudentId("other");profile.setYearLevel(YearLevel.FIRST_YEAR);profile=profiles.saveAndFlush(profile);
  assertEquals(0,summaries.getStudentSubjects(other).get(0).getCompletedWeeks());assertFalse(progression.getWeekAccess(subjectId,other).get(0).getCompleted());
  profile.setYearLevel(YearLevel.SECOND_YEAR);profiles.saveAndFlush(profile);
  assertTrue(summaries.getStudentSubjects(other).isEmpty());
  final var caller=other;var error=assertThrows(ResponseStatusException.class,()->progression.getWeekAccess(subjectId,caller));assertEquals(403,error.getStatusCode().value());
 }
}
