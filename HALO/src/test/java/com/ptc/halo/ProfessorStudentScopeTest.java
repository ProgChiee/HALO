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
 @Autowired jakarta.persistence.EntityManager entityManager;
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
 @Test void listIncludesOnlyEligibleStudentsAndSharedStudentsRemainVisible() {
  assertEquals(java.util.List.of(student.getId()),service.getStudents(a).stream().map(r->r.getUserId()).toList());
  assertEquals(java.util.List.of(student.getId()),service.getStudents(b).stream().map(r->r.getUserId()).toList());
 }
 @Test void paginatedSummariesScopeCountsAndKeepZeroActivityStudents() {
  var eligible=new StudentProfileEntity();eligible.setUser(empty);eligible.setStudentId("S2");eligible.setYearLevel(YearLevel.FIRST_YEAR);profiles.saveAndFlush(eligible);
  var outside=user("outside",Role.STUDENT);var excluded=new StudentProfileEntity();excluded.setUser(outside);excluded.setStudentId("S3");excluded.setYearLevel(YearLevel.SECOND_YEAR);profiles.saveAndFlush(excluded);
  var stats=entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
  stats.setStatisticsEnabled(true);entityManager.flush();entityManager.clear();stats.clear();
  var first=service.getProgressSummaries(a,0,1);
  assertEquals(2,first.getTotalElements());assertEquals(2,first.getTotalPages());
  var row=first.getContent().get(0);
  assertEquals(student.getId(),row.userId());assertEquals(1,row.completedModules());assertEquals(1,row.passedAssessments());assertEquals(1,row.totalBadges());
  assertEquals(2,stats.getPrepareStatementCount());assertEquals(0,stats.getEntityLoadCount());
  var second=service.getProgressSummaries(a,1,1);assertTrue(second.isLast());
  row=second.getContent().get(0);assertEquals(empty.getId(),row.userId());assertEquals(0,row.completedModules());assertEquals(0,row.passedAssessments());assertEquals(0,row.totalBadges());
  assertEquals(1,service.getProgressSummaries(b,0,20).getContent().get(0).completedModules());
  var c=user("c",Role.PROFESSOR);
  assertTrue(service.getProgressSummaries(c,0,20).stream().allMatch(r->r.completedModules()==0 && r.passedAssessments()==0));
  assertEquals(100,service.getProgressSummaries(a,0,1000).getSize());
  assertTrue(service.getProgressSummaries(a,4,20).isEmpty());
  assertEquals(400,assertThrows(ResponseStatusException.class,()->service.getProgressSummaries(a,-1,20)).getStatusCode().value());
  assertEquals(400,assertThrows(ResponseStatusException.class,()->service.getProgressSummaries(a,0,0)).getStatusCode().value());
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.getProgressSummaries(student,0,20));
 }
 @Test void lifecycleFiltersCountsWithoutRemovingAcademicHistory() {
  long attemptsBefore=attempts.count(), progressBefore=progress.count(), badgesBefore=badges.count();
  for(Status status:java.util.List.of(Status.INACTIVE,Status.ARCHIVED,Status.BLOCKED)) {
   student.setStatus(status);users.saveAndFlush(student);
   assertEquals(0,service.getProgressSummaries(a,0,20).getTotalElements());
   var former=service.getProgressSummaries(a,0,20,ProfessorStudentView.FORMER);
   assertEquals(status==Status.BLOCKED?0:1,former.getTotalElements());
   if(status!=Status.BLOCKED){assertEquals(status,former.getContent().get(0).status());assertEquals(1,former.getContent().get(0).completedModules());assertEquals(1,former.getContent().get(0).passedAssessments());}
   if(status!=Status.BLOCKED)assertEquals(1,service.getStudentAssessmentHistory(student.getId(),a).size());
   assertEquals(attemptsBefore,attempts.count());assertEquals(progressBefore,progress.count());assertEquals(badgesBefore,badges.count());
  }
  student.setStatus(Status.ACTIVE);users.saveAndFlush(student);
  assertEquals(1,service.getProgressSummaries(a,0,20).getTotalElements());assertTrue(service.getProgressSummaries(a,0,20,ProfessorStudentView.FORMER).isEmpty());
  var unrelated=user("unrelated",Role.PROFESSOR);assertTrue(service.getProgressSummaries(unrelated,0,20).isEmpty());
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
 @Test void noOwnedSubjectsAndMissingProfilesCannotAccessAnyStudentDetail() {
  var c=user("c",Role.PROFESSOR);
  assertTrue(service.getStudents(c).isEmpty());
  assertDetailsDenied(student.getId(),c);
  assertDetailsDenied(empty.getId(),a);
 }
 @Test void eligibleStudentWithoutActivityRemainsAccessible() {
  var profile=new StudentProfileEntity();profile.setUser(empty);profile.setStudentId("S2");profile.setYearLevel(YearLevel.FIRST_YEAR);profiles.saveAndFlush(profile);
  assertEquals(2,service.getStudents(a).size());
  assertEquals(0,service.getStudentProgress(empty.getId(),a).getCompletedModules());
  assertTrue(service.getStudentAssessmentHistory(empty.getId(),a).isEmpty());
  assertTrue(service.getStudentBadges(empty.getId(),a).isEmpty());
 }
 @Test void exclusiveStudentCannotBeAccessedByAnotherProfessorEvenWithHistoricalActivity() {
  var subject=subjects.findBySubjectCode("B").orElseThrow();
  subject.setYearLevel(YearLevel.SECOND_YEAR);subjects.saveAndFlush(subject);
  var profile=new StudentProfileEntity();profile.setUser(empty);profile.setStudentId("S2");profile.setYearLevel(YearLevel.SECOND_YEAR);profiles.saveAndFlush(profile);
  // A and B used to share student; historical activity does not grant current eligibility.
  assertEquals(java.util.List.of(student.getId()),service.getStudents(a).stream().map(r->r.getUserId()).toList());
  assertEquals(java.util.List.of(empty.getId()),service.getStudents(b).stream().map(r->r.getUserId()).toList());
  assertDetailsDenied(empty.getId(),a);assertDetailsDenied(student.getId(),b);
  assertEquals(empty.getId(),service.getStudentProgress(empty.getId(),b).getUserId());
  assertEquals(3,attempts.count());assertEquals(3,progress.count());assertEquals(1,badges.count());
 }
 @Test void multipleOwnedSubjectsDoNotDuplicateStudentsAndUnknownYearDoesNotGrantAccess() {
  var duplicate=new SubjectEntity();duplicate.setProfessor(subjects.findById(ownedSubject).orElseThrow().getProfessor());duplicate.setSubjectCode("A2");duplicate.setSubjectName("A2");duplicate.setYearLevel(YearLevel.FIRST_YEAR);subjects.saveAndFlush(duplicate);
  assertEquals(1,service.getStudents(a).size());
  var profile=new StudentProfileEntity();profile.setUser(empty);profile.setStudentId("S2");profiles.saveAndFlush(profile);
  assertDetailsDenied(empty.getId(),a);assertEquals(1,service.getStudents(a).size());
 }
 @Test void serviceRejectsNonProfessorActorsForAllLegacyReads() {
  for(var role:java.util.List.of(Role.STUDENT,Role.ADMIN,Role.SUPER_ADMIN)) {
   var actor=user("actor"+role,role);
   assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.getStudents(actor));
   for(var action:detailActions(student.getId(),actor)) assertThrows(org.springframework.security.access.AccessDeniedException.class,action::run);
  }
 }
 private java.util.List<Runnable> detailActions(Long id,UserEntity actor) {
  return java.util.List.of(()->service.getStudentProgress(id,actor),()->service.getStudentSubjects(id,actor),()->service.getStudentAssessmentHistory(id,actor),()->service.getStudentBadges(id,actor));
 }
 private void assertDetailsDenied(Long id,UserEntity actor) {
  for(var action:detailActions(id,actor)) {
   var error=assertThrows(ResponseStatusException.class,action::run);
   assertEquals(404,error.getStatusCode().value());assertEquals("RESOURCE_NOT_FOUND",error.getReason());
  }
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
