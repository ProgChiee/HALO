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
@Import({AdminProfessorMonitoringService.class,AdminStudentMonitoringService.class,AdminDashboardService.class,ActivityLogService.class})
class AdminMonitoringPagingTest {
 @Autowired AdminProfessorMonitoringService professorService;
 @Autowired AdminDashboardService dashboard;
 @Autowired AdminStudentMonitoringService studentMonitoring;
 @Autowired ActivityLogService logService;
 @Autowired ActivityLogRepository logs;

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

 org.hibernate.stat.Statistics statistics() {
  var stats=entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
  stats.setStatisticsEnabled(true);entityManager.flush();entityManager.clear();stats.clear();return stats;
 }
 @Test void pagesKeepSummariesAndBoundQueryCount() {
  logService.createLog(a,ActivityType.MODULE,"one");logService.createLog(a,ActivityType.MODULE,"two");
  var stats=statistics();
  var p=professorService.getProfessorMonitoring(0,1);
  assertEquals(2,p.totalElements());assertEquals(2,p.totalPages());assertEquals(1,p.content().size());
  assertEquals(2,p.content().get(0).getModuleActivities());assertEquals(2,p.summary().getModuleActivities());
  assertEquals(4,stats.getPrepareStatementCount());assertEquals(0,stats.getEntityLoadCount());
  for(int i=0;i<30;i++){var u=user("prof"+i,Role.PROFESSOR);var profile=new ProfessorEntity();profile.setUser(u);profile.setProfessorId("P"+i);professors.save(profile);}
  stats=statistics();p=professorService.getProfessorMonitoring(1,1);assertEquals(32,p.totalElements());
  assertEquals(4,stats.getPrepareStatementCount());assertEquals(0,stats.getEntityLoadCount());
  var zero=new StudentProfileEntity();zero.setUser(empty);zero.setStudentId("EMPTY");profiles.save(zero);
  stats=statistics();var result=studentMonitoring.getStudentMonitoring(0,1);
  assertEquals(2,result.totalElements());assertEquals(2,result.totalPages());var row=result.content().get(0);
  assertEquals(3,row.getCompletedModules());assertEquals(3,row.getPassedAssessments());assertEquals(1,row.getTotalBadges());assertEquals(90,row.getLatestAssessmentScore());
  assertEquals(90.0,result.summary().getAverageScore());assertEquals(3,result.summary().getPassedAssessments());
  assertEquals(4,stats.getPrepareStatementCount());assertEquals(0,stats.getEntityLoadCount());
  var second=studentMonitoring.getStudentMonitoring(1,1).content().get(0);assertEquals(0,second.getCompletedModules());assertNull(second.getLatestAssessmentScore());
  for(int i=0;i<30;i++){var u=user("student"+i,Role.STUDENT);var profile=new StudentProfileEntity();profile.setUser(u);profile.setStudentId("S"+i+"extra");profiles.save(profile);}
  stats=statistics();result=studentMonitoring.getStudentMonitoring(0,1);assertEquals(32,result.totalElements());
  assertEquals(4,stats.getPrepareStatementCount());assertEquals(0,stats.getEntityLoadCount());
  assertEquals(100,studentMonitoring.getStudentMonitoring(0,1000).size());
  assertEquals(100,professorService.getProfessorMonitoring(0,1000).size());
 }
 @Test void logFilteringIsPagedAndAllActorRolesRemainVisible() {
  var superadmin=user("super",Role.SUPER_ADMIN);
  for(int i=0;i<7;i++)logService.createLog(superadmin,ActivityType.ACCOUNT,"Unique search "+i);
  logService.createLog(a,ActivityType.MODULE,"Other");
  var stats=statistics();var page=logService.getLogs(null,ActivityType.ACCOUNT,0,3,"unique");
  assertEquals(7,page.getTotalElements());assertEquals(3,page.getTotalPages());assertEquals(3,page.getContent().size());
  assertTrue(page.stream().allMatch(r->r.getUserRole()==Role.SUPER_ADMIN && r.getUserName().equals("super")));
  assertEquals(2,stats.getPrepareStatementCount());
  var next=logService.getLogs(null,ActivityType.ACCOUNT,1,3,"unique");
  assertTrue(page.getContent().get(2).getId()>next.getContent().get(0).getId());
  assertEquals(0,logService.getLogs(Role.PROFESSOR,ActivityType.ACCOUNT,0,20,"").getTotalElements());
  assertEquals(8,logService.getLogs(null,null,0,1000,"").getTotalElements());
  assertEquals(100,logService.getLogs(null,null,0,1000,"").getSize());
 }

 @Test void currentMonitoringExcludesNonActiveAccountsWithoutLosingHistory() {
  logService.createLog(a,ActivityType.MODULE,"Preserved module action");
  var zero=new StudentProfileEntity();zero.setUser(empty);zero.setStudentId("ZERO");profiles.saveAndFlush(zero);
  long attemptCount=attempts.count(),progressCount=progress.count(),badgeCount=badges.count(),subjectCount=subjects.count(),moduleCount=modules.count(),logCount=logs.count();
  for(Status status:new Status[]{Status.INACTIVE,Status.ARCHIVED,Status.BLOCKED}) {
   student.setStatus(status);a.setStatus(status);users.saveAndFlush(student);users.saveAndFlush(a);
   var students=studentMonitoring.getStudentMonitoring(0,1);
   assertEquals(1,students.totalElements());assertEquals(1,students.totalPages());
   assertEquals(empty.getId(),students.content().get(0).getUserId());
   assertEquals(1,students.summary().getTotal());assertEquals(0,students.summary().getScored());assertNull(students.summary().getAverageScore());
   assertEquals(0,students.summary().getPassedAssessments());assertTrue(students.highlights().isEmpty());
   var professors=professorService.getProfessorMonitoring(0,1);
   assertEquals(1,professors.totalElements());assertEquals(1,professors.summary().getTotal());assertEquals(1,professors.summary().getActive());
   assertEquals(0,professors.summary().getModuleActivities());assertEquals(b.getId(),professors.content().get(0).getUserId());
   assertTrue(professors.highlights().stream().noneMatch(row->row.getUserId().equals(a.getId())));
   assertEquals(attemptCount,attempts.count());assertEquals(progressCount,progress.count());assertEquals(badgeCount,badges.count());
   assertEquals(subjectCount,subjects.count());assertEquals(moduleCount,modules.count());assertEquals(logCount,logs.count());
   assertEquals(1,logService.getLogs(Role.PROFESSOR,null,0,20,"").getTotalElements());
  }
  student.setStatus(Status.ACTIVE);a.setStatus(Status.ACTIVE);users.saveAndFlush(student);users.saveAndFlush(a);
  var restored=studentMonitoring.getStudentMonitoring(0,1);
  assertEquals(2,restored.totalElements());assertEquals(2,restored.totalPages());assertEquals(student.getId(),restored.content().get(0).getUserId());
  assertEquals(90,restored.content().get(0).getLatestAssessmentScore());assertEquals(90.0,restored.summary().getAverageScore());
  assertEquals(3,restored.summary().getPassedAssessments());assertTrue(restored.highlights().isEmpty());
  var restoredProfessors=professorService.getProfessorMonitoring(0,1);
  assertEquals(2,restoredProfessors.totalElements());assertEquals(1,restoredProfessors.summary().getModuleActivities());
  assertEquals(a.getId(),restoredProfessors.highlights().get(0).getUserId());
 }

 @Test void supportHighlightsUseLatestScoreThresholdWhileRankingKeepsAllStudents() {
  var assessment=assessments.findAll().get(0);
  for(Integer score:new Integer[]{100,80,75,65,60,59,0,null}) {
   var u=user("score"+score,Role.STUDENT);
   var p=new StudentProfileEntity();p.setUser(u);p.setStudentId("SCORE"+score);profiles.saveAndFlush(p);
   var attempt=new AssessmentAttemptEntity();attempt.setStudent(u);attempt.setAssessment(assessment);
   attempt.setPassed(score != null && score >= 75);attempt.setScore(score == null ? 0 : score);attempt.setStartedAt(LocalDateTime.now());attempt.setSubmittedAt(score == null ? null : LocalDateTime.now());attempts.saveAndFlush(attempt);
  }
  var p=new StudentProfileEntity();p.setUser(empty);p.setStudentId("NO-ATTEMPTS");profiles.saveAndFlush(p);
  var result=studentMonitoring.getStudentMonitoring(0,25);
  assertEquals(10,result.totalElements());
  assertEquals(java.util.List.of(0,59),result.highlights().stream().map(r->r.getLatestAssessmentScore()).toList());
  assertTrue(result.content().stream().map(r->r.getLatestAssessmentScore()).toList().containsAll(java.util.List.of(100,80,65,59,0)));
  // A later good score removes a previously struggling student from support, not ranking.
  var low=users.findByEmail("score59@example.test").orElseThrow();
  var latest=new AssessmentAttemptEntity();latest.setStudent(low);latest.setAssessment(assessment);latest.setScore(100);latest.setPassed(true);
  latest.setStartedAt(LocalDateTime.now());latest.setSubmittedAt(LocalDateTime.now().plusSeconds(1));attempts.saveAndFlush(latest);
  assertTrue(studentMonitoring.getStudentMonitoring(0,25).highlights().stream().noneMatch(r->r.getUserId().equals(low.getId())));
 }

 @Test void dashboardAndMonitoringCountActiveUsersIncludingMissingProfiles() {
  for(Status status:new Status[]{Status.INACTIVE,Status.ARCHIVED,Status.BLOCKED}) {
   var u=user("excluded"+status,Role.STUDENT);u.setStatus(status);users.saveAndFlush(u);
   var p=new StudentProfileEntity();p.setUser(u);p.setStudentId("EX"+status);profiles.saveAndFlush(p);
  }
  var result=studentMonitoring.getStudentMonitoring(0,1);
  assertEquals(2,dashboard.getDashboard().getTotalStudents());
  assertEquals(2,result.totalElements());assertEquals(2,result.summary().getTotal());assertEquals(2,result.totalPages());
  assertTrue(result.content().get(0).getProfilePresent());
  var missing=studentMonitoring.getStudentMonitoring(1,1).content().get(0);
  assertEquals(empty.getId(),missing.getUserId());assertFalse(missing.getProfilePresent());
  assertNull(missing.getStudentId());assertNull(missing.getYearLevel());assertNull(missing.getLatestAssessmentScore());
  assertEquals(0,missing.getCompletedModules());
  assertEquals(3,attempts.count());assertEquals(3,progress.count());assertEquals(1,badges.count());
 }

 @Test void incompleteProfilesRemainInActivePopulation() {
  var complete=profiles.findByUserId(student.getId()).orElseThrow();complete.setSection("A");profiles.saveAndFlush(complete);
  var studentRows=studentMonitoring.getStudentMonitoring(0,25).content();
  assertFalse(studentRows.stream().filter(r->r.getUserId().equals(student.getId())).findFirst().orElseThrow().getProfileIncomplete());
  assertTrue(studentRows.stream().filter(r->r.getUserId().equals(empty.getId())).findFirst().orElseThrow().getProfileIncomplete());
  complete.setSection(" ");profiles.saveAndFlush(complete);
  assertTrue(studentMonitoring.getStudentMonitoring(0,25).content().stream().filter(r->r.getUserId().equals(student.getId())).findFirst().orElseThrow().getProfileIncomplete());
  var missing=user("missingProfessor",Role.PROFESSOR);
  var blank=user("blankProfessor",Role.PROFESSOR);var p=new ProfessorEntity();p.setUser(blank);p.setProfessorId(" ");professors.saveAndFlush(p);
  var result=professorService.getProfessorMonitoring(0,25);
  assertEquals(4,result.totalElements());assertEquals(4,result.summary().getActive());
  assertEquals(2,result.content().stream().filter(r->r.getProfileIncomplete()).count());
  assertFalse(result.content().stream().filter(r->r.getUserId().equals(a.getId())).findFirst().orElseThrow().getProfileIncomplete());
  for(Status status:new Status[]{Status.INACTIVE,Status.ARCHIVED,Status.BLOCKED}) {
   missing.setStatus(status);users.saveAndFlush(missing);
   assertEquals(3,professorService.getProfessorMonitoring(0,25).totalElements());
  }
 }
}
