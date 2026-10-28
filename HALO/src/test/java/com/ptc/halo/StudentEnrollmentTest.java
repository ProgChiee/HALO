package com.ptc.halo;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties="spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",showSql=false)
@Import({StudentCurriculumService.class,StudentEnrollmentService.class,StudentSubjectService.class,StudentLessonAccessService.class,StudentLearningProgressionService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class StudentEnrollmentTest {
 @Autowired StudentEnrollmentService service;
 @Autowired StudentCurriculumService curriculum;
 @Autowired StudentSubjectService summaries;
 @Autowired StudentLessonAccessService access;
 @Autowired StudentLearningProgressionService progression;
 @Autowired UserRepository users;
 @Autowired StudentProfileRepository profiles;
 @Autowired StudentSubjectEnrollmentRepository enrollments;
 @Autowired EntityManager em;
 @Autowired PlatformTransactionManager manager;
 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 @MockBean ActivityLogService activity;
 UserEntity student; SubjectEntity eligible,foreign,draft; AiLearningModuleEntity module;
 @BeforeEach void setup(){
  student=new UserEntity();student.setName("Synthetic enrollment student");student.setEmail(UUID.randomUUID()+"@example.test");student.setPassword("unchanged");student.setRole(Role.STUDENT);student.setStatus(Status.ACTIVE);student=users.saveAndFlush(student);
  var profile=new StudentProfileEntity();profile.setUser(student);profile.setStudentId("legacy-"+student.getId());profile.setSection("BSHM 1A");profile.setYearLevel(YearLevel.FIRST_YEAR);profiles.saveAndFlush(profile);
  new TransactionTemplate(manager).executeWithoutResult(tx->{eligible=subject(YearLevel.FIRST_YEAR,true);foreign=subject(YearLevel.SECOND_YEAR,true);draft=subject(YearLevel.FIRST_YEAR,false);});
 }
 SubjectEntity subject(YearLevel year,boolean published){
  var s=new SubjectEntity();s.setSubjectCode(UUID.randomUUID().toString());s.setSubjectName("Synthetic subject");s.setYearLevel(year);em.persist(s);
  var w=new WeekEntity();w.setSubject(s);w.setWeekNumber(1);w.setTitle("Week 1");em.persist(w);
  var m=new AiLearningModuleEntity();m.setWeek(w);m.setStatus(published?LessonStatus.APPROVED:LessonStatus.PENDING);m.setAiGenerationStatus(AiGenerationStatus.COMPLETED);em.persist(m);if(year==YearLevel.FIRST_YEAR && published)module=m;return s;
 }
 @Test void curriculumOnlyExposesPublishedMetadataWithoutAcademicWrites() throws Exception {
  new TransactionTemplate(manager).executeWithoutResult(tx->{
   var published=em.find(AiLearningModuleEntity.class,module.getId());published.setLessonText("PRIVATE_LESSON");published.setAiNotes("PRIVATE_PROMPT");published.setYoutubeLink("PRIVATE_URL");
   for(int number:List.of(2,3)){
    var w=new WeekEntity();w.setSubject(eligible);w.setWeekNumber(number);w.setTitle("PRIVATE_DRAFT_"+number);em.persist(w);
    var m=new AiLearningModuleEntity();m.setWeek(w);m.setStatus(number==2?LessonStatus.PENDING:LessonStatus.APPROVED);m.setAiGenerationStatus(number==2?AiGenerationStatus.COMPLETED:AiGenerationStatus.FAILED);em.persist(m);
   }
  });
  var tables=List.of("student_subject_enrollments","student_module_progress","assessment_attempts","mentor_sessions","lesson_study_days","activity_log_entity");
  var before=tables.stream().map(t->jdbc.queryForObject("select count(*) from "+t,Long.class)).toList();
  var response=curriculum.preview(student,eligible.getId());
  assertEquals(1,response.publishedWeekCount());assertEquals("Week 1",response.weeks().get(0).title());
  var json=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(response);
  assertEquals(Set.of("subjectId","subjectCode","subjectName","yearLevel","publishedWeekCount","weeks"),fieldNames(json));
  assertEquals(Set.of("weekNumber","title"),fieldNames(json.get("weeks").get(0)));
  assertFalse(json.toString().contains("PRIVATE"));
  assertEquals(before,tables.stream().map(t->jdbc.queryForObject("select count(*) from "+t,Long.class)).toList());
  org.mockito.Mockito.verifyNoInteractions(activity);
 }
 private Set<String> fieldNames(com.fasterxml.jackson.databind.JsonNode node){var names=new HashSet<String>();node.fieldNames().forEachRemaining(names::add);return names;}
 @Test void curriculumRejectsUnauthorizedUnpublishedEnrolledAndIncompleteProfiles(){
  for(Long id:List.of(foreign.getId(),draft.getId(),Long.MAX_VALUE))assertEquals(404,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->curriculum.preview(student,id)).getStatusCode().value());
  student.setStatus(Status.INACTIVE);assertEquals(403,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->curriculum.preview(student,eligible.getId())).getStatusCode().value());student.setStatus(Status.ACTIVE);
  var p=profiles.findByUserId(student.getId()).orElseThrow();p.setSection("");profiles.saveAndFlush(p);
  assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->curriculum.preview(student,eligible.getId())).getStatusCode().value());
  p.setSection("BSHM 1A");profiles.saveAndFlush(p);enroll(eligible.getId());
  assertEquals(404,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->curriculum.preview(student,eligible.getId())).getStatusCode().value());
 }
 void enroll(Long id){service.enroll(TestIdentity.auth(student),id);}
 @Test void separatesListsReusesProgressAndKeepsLegacyAccess(){
  assertTrue(service.list(student,true).isEmpty());assertTrue(service.list(student,false).stream().anyMatch(s->s.getSubjectId().equals(eligible.getId())));
  assertTrue(service.list(student,false).stream().noneMatch(s->s.getSubjectId().equals(draft.getId())||s.getSubjectId().equals(foreign.getId())));
  // Legacy eligibility permits browsing but no longer authorizes learning.
  assertFalse(summaries.getStudentSubjects(student).get(0).isEnrolled());
  var denied=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->new TransactionTemplate(manager).executeWithoutResult(tx->progression.validateModuleAccess(module.getId(),student)));
  assertEquals("ENROLLMENT_REQUIRED",denied.getReason());
  var before=summaries.getStudentSubjects(student).stream().filter(s->s.getSubjectId().equals(eligible.getId())).findFirst().orElseThrow().getProgressPercentage();
  enroll(eligible.getId());enroll(eligible.getId());
  assertEquals(List.of(eligible.getId()),enrollments.subjectIds(student.getId()));
  assertEquals(before,service.list(student,true).get(0).getProgressPercentage());assertTrue(service.list(student,true).get(0).isEnrolled());
  assertTrue(service.list(student,false).stream().noneMatch(s->s.getSubjectId().equals(eligible.getId())));
  new TransactionTemplate(manager).executeWithoutResult(tx->progression.validateModuleAccess(module.getId(),student));
 }
 @Test void rejectsForeignUnpublishedMissingAndIncompleteSubjects(){
  for(Long id:List.of(foreign.getId(),draft.getId(),Long.MAX_VALUE))assertThrows(org.springframework.web.server.ResponseStatusException.class,()->enroll(id));
  var p=profiles.findByUserId(student.getId()).orElseThrow();p.setSection(" ");profiles.saveAndFlush(p);
  assertEquals("PROFILE_INCOMPLETE",assertThrows(org.springframework.web.server.ResponseStatusException.class,()->enroll(eligible.getId())).getReason());
  assertTrue(enrollments.subjectIds(student.getId()).isEmpty());
 }
 @Test void lifecycleRoleAndRevokedTokenFailClosed(){
  var auth=TestIdentity.auth(student);student.setTokenVersion(1);users.saveAndFlush(student);
  assertThrows(org.springframework.security.authentication.BadCredentialsException.class,()->service.enroll(auth,eligible.getId()));
  for(Status status:List.of(Status.INACTIVE,Status.ARCHIVED,Status.BLOCKED)){student.setStatus(status);users.saveAndFlush(student);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->enroll(eligible.getId()));}
  student.setStatus(Status.ACTIVE);student.setRole(Role.ADMIN);users.saveAndFlush(student);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->enroll(eligible.getId()));
 }
 @Test void concurrentRetriesHaveOneEnrollmentAndUniqueDatabaseConstraint() throws Exception {
  var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{Callable<Boolean> task=()->{start.await();enroll(eligible.getId());return true;};var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertTrue(a.get(15,TimeUnit.SECONDS));assertTrue(b.get(15,TimeUnit.SECONDS));assertEquals(1,enrollments.subjectIds(student.getId()).size());}
  finally{pool.shutdownNow();}
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->enrollments.saveAndFlush(new StudentSubjectEnrollmentEntity(student,eligible)));
 }
 @Test void enrollmentDoesNotRewriteLearningHistoryOrIdentity() throws Exception {
  new TransactionTemplate(manager).executeWithoutResult(tx->{
   em.persist(new LessonStudyDayEntity(student.getId(),module.getId(),java.time.LocalDate.of(2026,1,1)));
   var quiz=new AssessmentEntity();quiz.setModule(module);quiz.setStatus(AssessmentStatus.RETIRED);quiz.setQuizVersion(1);quiz.setTitle("Historical quiz");quiz.setPassingScore(75);quiz.setCreatedAt(java.time.LocalDateTime.of(2026,1,1,12,0));em.persist(quiz);
   var attempt=new AssessmentAttemptEntity();attempt.setAssessment(quiz);attempt.setStudent(student);attempt.setStartedAt(quiz.getCreatedAt());attempt.setSubmittedAt(quiz.getCreatedAt());attempt.setScore(20);attempt.setPassed(false);em.persist(attempt);
   var progress=new StudentModuleProgressEntity();progress.setStudent(student);progress.setModule(module);progress.setCompleted(true);progress.setCompletedAt(quiz.getCreatedAt());em.persist(progress);
   var session=new MentorSessionEntity();session.setStudent(student);session.setModule(module);session.setStartedAt(quiz.getCreatedAt());em.persist(session);
   var message=new MentorMessageEntity();message.setSession(session);message.setSender(MessageSender.STUDENT);message.setMessage("Preserved conversation");message.setCreatedAt(quiz.getCreatedAt());em.persist(message);
   var badge=new StudentBadgeEntity();badge.setStudent(student);badge.setBadgeType(BadgeType.values()[0]);badge.setBadgeName("Historical badge");badge.setDescription("Preserved");badge.setEarnedAt(quiz.getCreatedAt());em.persist(badge);
  });
  String review=java.nio.file.Files.readString(java.nio.file.Path.of("database/student-enrollment-participation-review.sql"));
  var candidates=jdbc.queryForList(review).stream().filter(row->((Number)row.get("student_id")).longValue()==student.getId()).toList();
  assertEquals(1,candidates.size());assertEquals("ASSESSMENT_ATTEMPT,COMPLETED_PROGRESS,LESSON_STUDY_DAY,STUDENT_MENTOR_MESSAGE",candidates.get(0).get("evidence_sources"));
  var before=snapshot();enroll(eligible.getId());assertEquals(before,snapshot());assertEquals(100,service.list(student,true).get(0).getProgressPercentage());
 }
 List<?> snapshot(){return List.of(jdbc.queryForList("select * from lesson_study_days"),jdbc.queryForList("select * from user_entity"),jdbc.queryForList("select * from assessment_attempts"),jdbc.queryForList("select * from assessments"),jdbc.queryForList("select * from student_module_progress"),jdbc.queryForList("select * from mentor_sessions"),jdbc.queryForList("select * from mentor_messages"),jdbc.queryForList("select * from student_badges"));}
}

