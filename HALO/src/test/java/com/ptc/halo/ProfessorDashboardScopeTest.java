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
@Import({ProfessorStudentService.class,StudentSubjectService.class,ProfessorDashboardService.class})
class ProfessorDashboardScopeTest {
 @Autowired ProfessorDashboardService dashboard;
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

 @Test void teachingCountsExcludeOtherOwnersAndUnassignedSubjects() {
  // Give B a different approval count to verify both directions of isolation.
  var foreign = modules.findAll().stream().filter(m -> m.getWeek().getSubject().getSubjectCode().equals("B")).findFirst().orElseThrow();
  foreign.setStatus(LessonStatus.PENDING); modules.saveAndFlush(foreign);
  var result = dashboard.getDashboard(a);
  assertEquals(1, result.getTotalSubjects());
  assertEquals(1, result.getTotalModules());
  assertEquals(1, result.getApprovedModules());
  assertEquals(1, result.getTotalAssessments());
  assertEquals(1, result.getTotalPassedAttempts());
  assertEquals(1, result.getTotalStudents()); // Only eligible ACTIVE Students.
  assertEquals(0, dashboard.getDashboard(b).getApprovedModules());
  assertEquals(1, dashboard.getDashboard(b).getTotalPassedAttempts());
 }
 @Test void professorWithoutOwnedDataHasZeroTeachingCounts() {
  var result = dashboard.getDashboard(user("no-subjects",Role.PROFESSOR));
  assertEquals(0,result.getTotalSubjects());assertEquals(0,result.getTotalModules());
  assertEquals(0,result.getApprovedModules());assertEquals(0,result.getTotalAssessments());assertEquals(0,result.getTotalPassedAttempts());
  assertEquals(0,result.getTotalStudents());
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->dashboard.getDashboard(student));
 }

 @Autowired LessonRevisionRepository revisions;
 @Autowired ActivityLogRepository logs;
 private AiLearningModuleEntity owned(){return modules.findAll().stream().filter(m->m.getWeek().getSubject().getId().equals(ownedSubject)).findFirst().orElseThrow();}
 @Test void lifecycleAndEligibilityFilterCurrentMetricsWithoutDeletingHistory(){
  var originalCount=attempts.count();
  for(var status:java.util.List.of(Status.INACTIVE,Status.ARCHIVED,Status.BLOCKED)){
   student.setStatus(status);users.saveAndFlush(student);var d=dashboard.getDashboard(a);
   assertEquals(0,d.getTotalStudents());assertEquals(0,d.getTotalPassedAttempts());assertTrue(d.getPerformance().isEmpty());assertEquals(originalCount,attempts.count());
  }
  student.setStatus(Status.ACTIVE);users.saveAndFlush(student);assertEquals(1,dashboard.getDashboard(a).getTotalStudents());
  var profile=profiles.findByUserId(student.getId()).orElseThrow();profile.setYearLevel(YearLevel.SECOND_YEAR);profiles.saveAndFlush(profile);
  assertEquals(0,dashboard.getDashboard(a).getTotalStudents());assertTrue(dashboard.getDashboard(a).getPerformance().isEmpty());assertEquals(originalCount,attempts.count());
 }
 @Test void performanceUsesOnlySubmittedScopedAttemptsWithinTwentyEightDays(){
  var ownAttempt=attempts.findAll().stream().filter(x->x.getAssessment().getModule().getId().equals(owned().getId())).findFirst().orElseThrow();
  var yesterday=new AssessmentAttemptEntity();yesterday.setAssessment(ownAttempt.getAssessment());yesterday.setStudent(student);yesterday.setScore(50);yesterday.setPassed(false);yesterday.setStartedAt(java.time.LocalDate.now().minusDays(1).atTime(10,0));yesterday.setSubmittedAt(yesterday.getStartedAt());attempts.saveAndFlush(yesterday);
  var old=new AssessmentAttemptEntity();old.setAssessment(ownAttempt.getAssessment());old.setStudent(student);old.setScore(10);old.setPassed(false);old.setStartedAt(java.time.LocalDate.now().minusDays(28).atTime(10,0));old.setSubmittedAt(old.getStartedAt());attempts.saveAndFlush(old);
  var unfinished=new AssessmentAttemptEntity();unfinished.setAssessment(ownAttempt.getAssessment());unfinished.setStudent(student);unfinished.setScore(0);unfinished.setPassed(false);unfinished.setStartedAt(LocalDateTime.now());attempts.saveAndFlush(unfinished);
  var d=dashboard.getDashboard(a);assertEquals(2,d.getPerformance().size());assertEquals(50.0,d.getPerformance().get(0).averageScore());assertEquals(90.0,d.getPerformance().get(1).averageScore());assertEquals(1,d.getPerformance().get(1).attempts());
  assertEquals(java.time.LocalDate.now().minusDays(27),d.getPerformanceFrom());
 }
 @Test void attentionUsesRealOwnedFlagsAndNeverChangesRevisionState(){
  var m=owned();m.setAssessmentNeedsReview(true);m.setStatus(LessonStatus.PENDING);modules.saveAndFlush(m);
  var revision=new LessonRevisionEntity();revision.moduleId=m.getId();revision.baseVersion=m.getVersion();revision.revisionNumber=1;revision.state="DRAFT";revision.payload="{}";revision.revisedBy=a.getId();revision.revisedAt=LocalDateTime.now();revisions.saveAndFlush(revision);
  var d=dashboard.getDashboard(a);for(var item:d.getAttention()){assertEquals(1,item.count());assertEquals(m.getId(),item.lessons().get(0).moduleId());assertEquals(ownedSubject,item.lessons().get(0).subjectId());}
  for(var item:dashboard.getDashboard(b).getAttention())assertEquals(0,item.count());
  assertEquals("DRAFT",revisions.findById(m.getId()).orElseThrow().state);assertTrue(m.isAssessmentNeedsReview());
 }
 private void log(UserEntity actor,UserEntity target,ActivityType type,String text){var l=new ActivityLogEntity();l.setUser(actor);l.setActingTarget(target);if(target!=null)l.setActingTargetRole(target.getRole());l.setActivityType(type);l.setAction(text);l.setCreatedAt(LocalDateTime.now());logs.saveAndFlush(l);}
 @Test void recentActivityIncludesOnlyOwnTeachingAndAdminActionsForThisProfessor(){
  log(a,null,ActivityType.MODULE,"Published revised lesson");log(b,null,ActivityType.MODULE,"Other Professor private lesson");log(student,null,ActivityType.ASSESSMENT,"Student private result");log(a,null,ActivityType.AUTH,"Login");
  var admin=user("admin",Role.ADMIN);log(admin,a,ActivityType.ASSESSMENT,"Approved quiz version 4 by Admin "+admin.getId()+" acting as PROFESSOR "+a.getId());
  var d=dashboard.getDashboard(a);assertEquals(2,d.getRecentActivity().size());assertTrue(d.getRecentActivity().get(0).description().contains("on behalf of a"));assertFalse(d.getRecentActivity().toString().contains("private"));assertEquals(5,logs.count());
 }

 @Test void unpublishedQuizDraftAlsoNeedsReviewWithoutInflatingAssessmentCount(){
  var q=new AssessmentEntity();q.setModule(owned());q.setQuizVersion(2);q.setTitle("Draft quiz");q.setPassingScore(70);q.setStatus(AssessmentStatus.DRAFT);q.setCreatedAt(LocalDateTime.now());assessments.saveAndFlush(q);
  var d=dashboard.getDashboard(a);assertEquals(1,d.getAttention().get(0).count());assertEquals(1,d.getTotalAssessments());assertFalse(owned().isAssessmentNeedsReview());
 }

 @Test void rosterIsDistinctAndMatchesCurrentProgressAcrossLifecycleStates(){
  var second=new SubjectEntity();second.setProfessor(subjects.findById(ownedSubject).orElseThrow().getProfessor());
  second.setSubjectCode("A2");second.setSubjectName("Another eligible subject");second.setYearLevel(YearLevel.FIRST_YEAR);subjects.saveAndFlush(second);
  var unrelated=user("unrelated",Role.STUDENT);var profile=new StudentProfileEntity();profile.setUser(unrelated);profile.setStudentId("S2");profile.setYearLevel(YearLevel.SECOND_YEAR);profiles.saveAndFlush(profile);
  long history=attempts.count();
  for(var status:java.util.List.of(Status.ACTIVE,Status.INACTIVE,Status.ARCHIVED,Status.BLOCKED,Status.ACTIVE)){
   student.setStatus(status);users.saveAndFlush(student);
   long expected=status==Status.ACTIVE?1:0;
   assertEquals(expected,dashboard.getDashboard(a).getTotalStudents());
   assertEquals(expected,service.getProgressSummaries(a,0,20).getTotalElements());
   assertEquals(history,attempts.count());
  }
 }
}
