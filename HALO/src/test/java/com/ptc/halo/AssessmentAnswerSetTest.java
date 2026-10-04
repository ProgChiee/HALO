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
@Import({AssessmentService.class,BadgeService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AssessmentAnswerSetTest {
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
 @MockBean StudentLearningProgressionService progression;
 @MockBean ActivityLogService audit;
 UserEntity student;AssessmentAttemptEntity attempt;AssessmentQuestionEntity q1,q2,foreign;
 @BeforeEach void setup(){
  reset(audit);badges.deleteAll();progress.deleteAll();answers.deleteAll();attempts.deleteAll();questions.deleteAll();assessments.deleteAll();modules.deleteAll();weeks.deleteAll();subjects.deleteAll();users.deleteAll();
  student=new UserEntity();student.setEmail("student@test.example");student.setName("Student");student.setRole(Role.STUDENT);student.setStatus(Status.ACTIVE);student=users.saveAndFlush(student);
  var subject=new SubjectEntity();subject.setSubjectCode("S");subject.setSubjectName("Subject");subject=subjects.saveAndFlush(subject);
  var week=new WeekEntity();week.setSubject(subject);week.setWeekNumber(1);week.setTitle("Week");week=weeks.saveAndFlush(week);
  var module=new AiLearningModuleEntity();module.setWeek(week);module.setStatus(LessonStatus.APPROVED);module=modules.saveAndFlush(module);
  var assessment=new AssessmentEntity();assessment.setModule(module);assessment.setTitle("Quiz");assessment.setPassingScore(70);assessment.setStatus(AssessmentStatus.AVAILABLE);assessment.setCreatedAt(LocalDateTime.now());assessment=assessments.saveAndFlush(assessment);
  q1=question(assessment,1,"A");q2=question(assessment,2,"B");
  attempt=new AssessmentAttemptEntity();attempt.setStudent(student);attempt.setAssessment(assessment);attempt.setStartedAt(LocalDateTime.now());attempt.setScore(0);attempt.setPassed(false);attempt=attempts.saveAndFlush(attempt);
 }
 AssessmentQuestionEntity question(AssessmentEntity assessment,int number,String correct){
  var q=new AssessmentQuestionEntity();q.setAssessment(assessment);q.setQuestionNumber(number);q.setQuestionText("Question");q.setOptionA("A");q.setOptionB("B");q.setOptionC("C");q.setOptionD("D");q.setCorrectAnswer(correct);return questions.saveAndFlush(q);
 }
 StudentAnswerRequest.AnswerItem item(Long id,String answer){var i=new StudentAnswerRequest.AnswerItem();i.setQuestionId(id);i.setAnswer(answer);return i;}
 StudentAnswerRequest request(StudentAnswerRequest.AnswerItem...items){var r=new StudentAnswerRequest();r.setAnswers(Arrays.asList(items));return r;}
 void rejected(StudentAnswerRequest request){
  var e=assertThrows(ResponseStatusException.class,()->service.submitAttempt(attempt.getId(),student,request));assertEquals(400,e.getStatusCode().value());assertEquals("INVALID_ANSWER_SET",e.getReason());
  assertEquals(0,answers.count());assertEquals(0,progress.count());assertEquals(0,badges.count());assertNull(attempts.findById(attempt.getId()).orElseThrow().getSubmittedAt());verifyNoInteractions(audit);
 }
 @Test void duplicateAndInflatedSetsAreRejected(){
  rejected(request(item(q1.getId(),"A"),item(q1.getId(),"A")));
  rejected(request(item(q1.getId(),"A"),item(q1.getId(),"A"),item(q1.getId(),"A"),item(q2.getId(),"B")));
 }
 @Test void foreignMissingNullAndInvalidAnswersAreRejected(){
  // A persisted question in a different assessment is not part of the expected set.
  var week=new WeekEntity();week.setSubject(subjects.findAll().get(0));week.setWeekNumber(2);week.setTitle("Other");week=weeks.saveAndFlush(week);
  var module=new AiLearningModuleEntity();module.setWeek(week);module=modules.saveAndFlush(module);
  var other=new AssessmentEntity();other.setModule(module);other.setTitle("Other");other.setPassingScore(70);other.setCreatedAt(LocalDateTime.now());other.setStatus(AssessmentStatus.AVAILABLE);other=assessments.saveAndFlush(other);
  foreign=question(other,1,"A");
  rejected(request(item(q1.getId(),"A"),item(foreign.getId(),"A")));
  rejected(request(item(q1.getId(),"A")));rejected(request(item(q1.getId(),"A"),null));
  rejected(request(item(q1.getId(),"A"),item(q2.getId(),"invalid")));rejected(null);
 }
 @Test void validUniqueSetGets100AndAwardsProgressAndBadges(){
  var result=service.submitAttempt(attempt.getId(),student,request(item(q2.getId()," b "),item(q1.getId(),"a")));
  assertEquals(100,result.getScore());assertTrue(result.getPassed());assertEquals(2,answers.count());assertEquals(1,progress.count());assertTrue(badges.count()>0);
 }
 @Test void incorrectAnswerGets50WithoutCompletion(){
  var result=service.submitAttempt(attempt.getId(),student,request(item(q1.getId(),"A"),item(q2.getId(),"C")));
  assertEquals(50,result.getScore());assertFalse(result.getPassed());assertEquals(2,answers.count());assertEquals(0,progress.count());
 }
 @Test void ownershipCheckStillRunsBeforeAnyAnswerWrites(){
  var stranger=new UserEntity();stranger.setId(Long.MAX_VALUE);
  assertThrows(RuntimeException.class,()->service.submitAttempt(attempt.getId(),stranger,request(item(q1.getId(),"A"),item(q2.getId(),"B"))));
  assertEquals(0,answers.count());assertEquals(0,progress.count());assertEquals(0,badges.count());verifyNoInteractions(audit);
 }
 @Test void databaseRejectsDuplicatePairAndServiceReturnsSafeConflict(){
  var saved=new StudentAnswerEntity();saved.setAttempt(attempt);saved.setQuestion(q1);saved.setAnswer("A");answers.saveAndFlush(saved);
  var duplicate=new StudentAnswerEntity();duplicate.setAttempt(attempt);duplicate.setQuestion(q1);duplicate.setAnswer("B");
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->answers.saveAndFlush(duplicate));
  var e=assertThrows(ResponseStatusException.class,()->service.submitAttempt(attempt.getId(),student,request(item(q1.getId(),"A"),item(q2.getId(),"B"))));
  assertEquals(409,e.getStatusCode().value());assertEquals("ANSWERS_ALREADY_RECORDED",e.getReason());assertEquals(1,answers.count());assertEquals(0,progress.count());assertEquals(0,badges.count());verifyNoInteractions(audit);
 }
}
