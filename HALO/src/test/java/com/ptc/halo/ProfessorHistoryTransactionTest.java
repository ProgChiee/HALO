package com.ptc.halo;

import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.open-in-view=false"},showSql=false)
@Import({ProfessorStudentService.class,StudentSubjectService.class})
@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ProfessorHistoryTransactionTest {
 @Autowired ProfessorStudentService service;
 @Autowired StudentSubjectService studentService;
 @Autowired UserRepository users;
 @Autowired ProfessorRepository professors;
 @Autowired StudentProfileRepository profiles;
 @Autowired SubjectRepository subjects;
 @Autowired WeekRepository weeks;
 @Autowired AiLearningModuleRepository modules;
 @Autowired StudentModuleProgressRepository progress;
 @Autowired AssessmentRepository assessments;
 @Autowired AssessmentAttemptRepository attempts;
 @Autowired StudentBadgeRepository badges;
 UserEntity a,b,student,empty;
 Long ownedSubject;
 @BeforeEach void setup() {
  a=user("a",Role.PROFESSOR); b=user("b",Role.PROFESSOR); student=user("student",Role.STUDENT); empty=user("empty",Role.STUDENT);
  var profile=new StudentProfileEntity();profile.setUser(student);profile.setStudentId("S1");profile.setYearLevel(YearLevel.FIRST_YEAR);profiles.saveAndFlush(profile);
  ownedSubject=activity(a,"A");activity(b,"B");activity(null,"Legacy");
  var badge=new StudentBadgeEntity();badge.setStudent(student);badge.setBadgeType(BadgeType.FIRST_STEP);badge.setBadgeName("First step");badge.setDescription("Institution-wide");badge.setEarnedAt(LocalDateTime.now());badges.saveAndFlush(badge);
 }
 UserEntity user(String name,Role role) {var u=new UserEntity();u.setName(name);u.setEmail(name+"@example.test");u.setRole(role);u.setStatus(Status.ACTIVE);return users.saveAndFlush(u);}
 Long activity(UserEntity owner,String name) {
  ProfessorEntity p=null;
  if(owner!=null){p=new ProfessorEntity();p.setUser(owner);p.setProfessorId(name);p=professors.saveAndFlush(p);}
  var s=new SubjectEntity();s.setProfessor(p);s.setSubjectCode(name);s.setSubjectName(name);s.setYearLevel(YearLevel.FIRST_YEAR);s=subjects.saveAndFlush(s);
  var w=new WeekEntity();w.setSubject(s);w.setTitle(name);w.setWeekNumber(1);w=weeks.saveAndFlush(w);
  var m=new AiLearningModuleEntity();m.setWeek(w);m.setStatus(LessonStatus.APPROVED);m.setAiGenerationStatus(AiGenerationStatus.COMPLETED);m=modules.saveAndFlush(m);
  var record=new StudentModuleProgressEntity();record.setStudent(student);record.setModule(m);record.setCompleted(true);progress.saveAndFlush(record);
  var assessment=new AssessmentEntity();assessment.setModule(m);assessment.setTitle(name);assessment.setPassingScore(75);assessment.setStatus(AssessmentStatus.AVAILABLE);assessment.setCreatedAt(LocalDateTime.now());assessment=assessments.saveAndFlush(assessment);
  var attempt=new AssessmentAttemptEntity();attempt.setStudent(student);attempt.setAssessment(assessment);attempt.setScore(90);attempt.setPassed(true);attempt.setStartedAt(LocalDateTime.now());attempt.setSubmittedAt(LocalDateTime.now());attempts.saveAndFlush(attempt);
  return s.getId();
 }
 @Test void mapsLazyHistoryInsideServiceTransactionWithNoCallerTransaction() {
  assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
  var detached = attempts.findByStudentIdAndAssessment_Module_Week_Subject_Professor_User_IdAndSubmittedAtIsNotNullOrderBySubmittedAtDesc(student.getId(), a.getId()).get(0);
  assertFalse(org.hibernate.Hibernate.isInitialized(detached.getAssessment()));
  assertThrows(org.hibernate.LazyInitializationException.class, () -> detached.getAssessment().getTitle());

  var history = assertDoesNotThrow(() -> service.getStudentAssessmentHistory(student.getId(), a));
  assertEquals(1, history.size());
  assertEquals("A", history.get(0).getAssessmentTitle());
  assertEquals("A", history.get(0).getSubjectName());
  assertEquals(1, history.get(0).getWeekNumber());
  assertEquals(90, history.get(0).getScore());
  assertEquals("B", service.getStudentAssessmentHistory(student.getId(), b).get(0).getSubjectName());
  assertTrue(service.getStudentAssessmentHistory(empty.getId(), a).isEmpty());
  var noSubjects = user("noSubjects", Role.PROFESSOR);
  assertTrue(service.getStudentAssessmentHistory(student.getId(), noSubjects).isEmpty());
  for(Long id : new Long[]{Long.MAX_VALUE, a.getId()}) {
   var error = assertThrows(ResponseStatusException.class, () -> service.getStudentAssessmentHistory(id, a));
   assertEquals(404, error.getStatusCode().value());
   assertEquals("RESOURCE_NOT_FOUND", error.getReason());
  }
  assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
 }
}
