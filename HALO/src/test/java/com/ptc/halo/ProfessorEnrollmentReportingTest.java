package com.ptc.halo;

import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties="spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",showSql=false)
@Import({ProfessorDashboardService.class,ProfessorStudentService.class,StudentEnrollmentService.class,StudentSubjectService.class,StudentLessonAccessService.class})
class ProfessorEnrollmentReportingTest {
 @Autowired EntityManager em;
 @Autowired ProfessorDashboardService dashboard;
 @Autowired ProfessorStudentService monitoring;
 @Autowired StudentEnrollmentService lifecycle;
 @Autowired StudentSubjectEnrollmentRepository enrollments;
 @Autowired StudentProfileRepository profiles;
 @Autowired JdbcTemplate jdbc;
 @MockBean ActivityLogService audit;
 ProfessorEngagementService engagement;
 UserEntity professor,other,student,never,third;
 SubjectEntity first,second,foreign,otherYear;
 AiLearningModuleEntity module;
 @BeforeEach void setup(){
  professor=user(Role.PROFESSOR);other=user(Role.PROFESSOR);
  var owner=owner(professor);var foreignOwner=owner(other);
  first=subject(owner,YearLevel.FIRST_YEAR);second=subject(owner,YearLevel.FIRST_YEAR);
  foreign=subject(foreignOwner,YearLevel.FIRST_YEAR);otherYear=subject(foreignOwner,YearLevel.SECOND_YEAR);
  student=student(YearLevel.FIRST_YEAR);never=student(YearLevel.FIRST_YEAR);third=student(YearLevel.SECOND_YEAR);
  em.flush();engagement=new ProfessorEngagementService(new NamedParameterJdbcTemplate(jdbc));
 }
 UserEntity user(Role role){var u=new UserEntity();u.setName("Synthetic");u.setEmail(UUID.randomUUID()+"@example.test");u.setPassword("unchanged");u.setRole(role);u.setStatus(Status.ACTIVE);em.persist(u);return u;}
 ProfessorEntity owner(UserEntity u){var p=new ProfessorEntity();p.setUser(u);p.setProfessorId(UUID.randomUUID().toString());em.persist(p);return p;}
 UserEntity student(YearLevel year){var u=user(Role.STUDENT);var p=new StudentProfileEntity();p.setUser(u);p.setStudentId("fixture-"+u.getId());p.setSection("BSHM 1A");p.setYearLevel(year);em.persist(p);return u;}
 SubjectEntity subject(ProfessorEntity owner,YearLevel year){var s=new SubjectEntity();s.setProfessor(owner);s.setSubjectCode(UUID.randomUUID().toString());s.setSubjectName("Synthetic subject");s.setYearLevel(year);em.persist(s);var w=new WeekEntity();w.setSubject(s);w.setWeekNumber(1);w.setTitle("Lesson");em.persist(w);var m=new AiLearningModuleEntity();m.setWeek(w);m.setStatus(LessonStatus.APPROVED);m.setAiGenerationStatus(AiGenerationStatus.COMPLETED);em.persist(m);if(module==null)module=m;return s;}
 void enroll(SubjectEntity s){lifecycle.enroll(TestIdentity.auth(student),s.getId());em.flush();}
 void leave(SubjectEntity s){lifecycle.unenroll(TestIdentity.auth(student),s.getId());em.flush();}
 List<Long> ids(ProfessorStudentView account,ProfessorEnrollmentView membership){return monitoring.getProgressSummaries(professor,0,100,account,membership).getContent().stream().map(r->r.userId()).toList();}
 void totals(long eligible,long active){var d=dashboard.getDashboard(professor);assertEquals(eligible,d.getTotalStudents());assertEquals(active,d.getActivelyEnrolledStudents());assertEquals(eligible-active,d.getEligibleStudentsWithoutActiveEnrollment());}
 @Test void enrollmentUnenrollmentAndReenrollmentUpdateReportingAndRetainSameRow(){
  totals(2,0);assertEquals(List.of(student.getId(),never.getId()),ids(ProfessorStudentView.CURRENT,ProfessorEnrollmentView.NEVER));
  enroll(first);totals(2,1);assertEquals(List.of(student.getId()),ids(ProfessorStudentView.CURRENT,ProfessorEnrollmentView.ACTIVE));
  var original=jdbc.queryForMap("select id,student_id,subject_id,recorded_at_epoch_ms,source from student_subject_enrollments where student_id=?",student.getId());
  leave(first);totals(2,0);assertEquals(Status.ACTIVE,student.getStatus());assertEquals(List.of(student.getId()),ids(ProfessorStudentView.CURRENT,ProfessorEnrollmentView.PREVIOUS));
  assertEquals(List.of(never.getId()),ids(ProfessorStudentView.CURRENT,ProfessorEnrollmentView.NEVER));
  enroll(first);totals(2,1);assertEquals(original,jdbc.queryForMap("select id,student_id,subject_id,recorded_at_epoch_ms,source from student_subject_enrollments where student_id=?",student.getId()));assertEquals(1,enrollments.count());
 }
 @Test void multipleSubjectsCountOnceAndActiveMembershipTakesPrecedence(){
  enroll(first);enroll(second);totals(2,1);leave(first);totals(2,1);
  assertTrue(ids(ProfessorStudentView.CURRENT,ProfessorEnrollmentView.PREVIOUS).isEmpty());
  var row=monitoring.getProgressSummaries(professor,0,20,ProfessorStudentView.CURRENT,ProfessorEnrollmentView.ACTIVE).getContent().get(0);assertEquals("ACTIVE",row.enrollmentStatus());
  leave(second);totals(2,0);assertEquals(List.of(student.getId()),ids(ProfessorStudentView.CURRENT,ProfessorEnrollmentView.PREVIOUS));
 }
 @Test void foreignEnrollmentNeverGrantsMembershipOrAcademicAccess(){
  enroll(foreign);totals(2,0);assertEquals(1,dashboard.getDashboard(other).getActivelyEnrolledStudents());
  assertTrue(ids(ProfessorStudentView.CURRENT,ProfessorEnrollmentView.NEVER).contains(student.getId()));
  var illicit=new StudentSubjectEnrollmentEntity(third,first);em.persist(illicit);em.flush();
  assertFalse(ids(ProfessorStudentView.CURRENT,ProfessorEnrollmentView.ALL).contains(third.getId()));totals(2,0);
  assertEquals(404,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->monitoring.getStudentProgress(third.getId(),professor)).getStatusCode().value());
 }
 @Test void yearChangesDoNotBroadenScopeOrEraseEnrollmentHistory(){
  enroll(first);var id=enrollments.findAll().get(0).getId();var p=profiles.findByUserId(student.getId()).orElseThrow();p.setYearLevel(YearLevel.SECOND_YEAR);em.flush();
  totals(1,0);assertFalse(ids(ProfessorStudentView.CURRENT,ProfessorEnrollmentView.ALL).contains(student.getId()));assertTrue(enrollments.findById(id).orElseThrow().isActive());
  // Being eligible for another owned year still must not count the old, mismatched enrollment.
  subject(em.find(ProfessorEntity.class,first.getProfessor().getId()),YearLevel.SECOND_YEAR);em.flush();totals(3,0);
  assertTrue(ids(ProfessorStudentView.CURRENT,ProfessorEnrollmentView.NEVER).contains(student.getId()));
 }
 @Test void accountLifecycleAndMembershipAreIndependent(){
  enroll(first);
  for(var status:List.of(Status.INACTIVE,Status.ARCHIVED)){student.setStatus(status);em.flush();totals(1,0);assertTrue(ids(ProfessorStudentView.CURRENT,ProfessorEnrollmentView.ACTIVE).isEmpty());assertEquals(List.of(student.getId()),ids(ProfessorStudentView.FORMER,ProfessorEnrollmentView.ACTIVE));assertTrue(ids(ProfessorStudentView.FORMER,ProfessorEnrollmentView.PREVIOUS).isEmpty());}
  student.setStatus(Status.ACTIVE);em.flush();totals(2,1);
 }
 @Test void filteringPrecedesStablePaginationAndTotals(){
  enroll(first);enroll(second);var extra=student(YearLevel.FIRST_YEAR);em.flush();
  var a=monitoring.getProgressSummaries(professor,0,1,ProfessorStudentView.CURRENT,ProfessorEnrollmentView.NEVER);
  var b=monitoring.getProgressSummaries(professor,1,1,ProfessorStudentView.CURRENT,ProfessorEnrollmentView.NEVER);
  assertEquals(2,a.getTotalElements());assertEquals(2,a.getTotalPages());assertEquals(never.getId(),a.getContent().get(0).userId());assertEquals(extra.getId(),b.getContent().get(0).userId());assertEquals("NEVER",b.getContent().get(0).enrollmentStatus());
  assertEquals(1,monitoring.getProgressSummaries(professor,0,1,ProfessorStudentView.CURRENT,ProfessorEnrollmentView.ACTIVE).getTotalElements());
 }
 @Test void engagementUsesSameFiltersAndKeepsHistoricalActivity(){
  em.persist(new LessonStudyDayEntity(student.getId(),module.getId(),LocalDate.of(2026,10,30)));em.flush();
  var before=jdbc.queryForList("select * from lesson_study_days");enroll(first);leave(first);
  for(var filter:ProfessorEnrollmentView.values()){
   var rows=engagement.get(professor,0,20,LocalDate.of(2026,10,30),ProfessorStudentView.CURRENT,filter);
   assertEquals(ids(ProfessorStudentView.CURRENT,filter),rows.content().stream().map(r->r.userId()).toList());
  }
  var former=engagement.get(professor,0,1,LocalDate.of(2026,10,30),ProfessorStudentView.CURRENT,ProfessorEnrollmentView.PREVIOUS);
  assertEquals(1,former.totalElements());assertEquals(1,former.content().get(0).activeDaysWeek());assertEquals("PREVIOUS",former.content().get(0).enrollmentStatus());
  enroll(first);assertEquals(before,jdbc.queryForList("select * from lesson_study_days"));
 }
 @Test void missingProfilesAndNonProfessorActorsRemainDenied(){
  var missing=user(Role.STUDENT);em.persist(new StudentSubjectEnrollmentEntity(missing,first));em.flush();totals(2,0);
  assertFalse(ids(ProfessorStudentView.CURRENT,ProfessorEnrollmentView.ALL).contains(missing.getId()));
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->monitoring.getProgressSummaries(student,0,20,ProfessorStudentView.CURRENT,ProfessorEnrollmentView.ACTIVE));
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->engagement.get(student,0,20,ProfessorStudentView.CURRENT,ProfessorEnrollmentView.ALL));
 }
}
