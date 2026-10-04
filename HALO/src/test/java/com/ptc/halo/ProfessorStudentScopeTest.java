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
class ProfessorStudentScopeTest {
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
 @Test void listIncludesStudentAccountsEvenWithoutProfile() {
  assertEquals(2,service.getStudents(a).size());assertEquals(2,service.getStudents(b).size());
 }
 @Test void progressHistoryAndSubjectsAreOwnedOnly() {
  var result=service.getStudentProgress(student.getId(),a);
  assertEquals(1,result.getCompletedModules());assertEquals(1,result.getPassedAssessments());
  var history=service.getStudentAssessmentHistory(student.getId(),a);assertEquals(1,history.size());assertEquals("A",history.get(0).getSubjectName());
  assertEquals("B",service.getStudentAssessmentHistory(student.getId(),b).get(0).getSubjectName());
  var summaries=service.getStudentSubjects(student.getId(),a);assertEquals(1,summaries.size());assertEquals(ownedSubject,summaries.get(0).getSubjectId());assertEquals(1,summaries.get(0).getCompletedWeeks());
  assertEquals(3,studentService.getStudentSubjects(student).size());
  assertEquals(3,progress.countByStudentIdAndCompletedTrue(student.getId()));assertEquals(3,attempts.countByStudentIdAndPassedTrue(student.getId()));
 }
 @Test void noScopedActivityReturnsZeroAndEmptyHistory() {
  var c=user("c",Role.PROFESSOR);
  assertEquals(0,service.getStudentProgress(student.getId(),c).getCompletedModules());assertEquals(0,service.getStudentProgress(student.getId(),c).getPassedAssessments());
  assertTrue(service.getStudentAssessmentHistory(student.getId(),c).isEmpty());assertTrue(service.getStudentSubjects(student.getId(),c).isEmpty());
  assertEquals(0,service.getStudentProgress(empty.getId(),a).getCompletedModules());assertTrue(service.getStudentAssessmentHistory(empty.getId(),a).isEmpty());
 }
 @Test void badgesRemainInstitutionWide() {
  assertEquals(1,service.getStudentBadges(student.getId(),a).size());assertEquals(1,service.getStudentBadges(student.getId(),b).size());
  assertEquals("INSTITUTION_WIDE",service.getStudentBadges(student.getId(),a).get(0).getBadgeScope());
  assertEquals(1,service.getStudentProgress(student.getId(),a).getTotalBadges());assertEquals("INSTITUTION_WIDE",service.getStudentProgress(student.getId(),a).getBadgeScope());
 }
 @Test void missingAndNonStudentIdsReturn404ForEveryDetail() {
  for(Long id:new Long[]{Long.MAX_VALUE,a.getId(),null}) {
   for(Runnable action:java.util.List.<Runnable>of(()->service.getStudentProgress(id,a),()->service.getStudentSubjects(id,a),()->service.getStudentAssessmentHistory(id,a),()->service.getStudentBadges(id,a))) {
    var e=assertThrows(ResponseStatusException.class,action::run);assertEquals(404,e.getStatusCode().value());assertEquals("RESOURCE_NOT_FOUND",e.getReason());
   }
  }
 }
}
