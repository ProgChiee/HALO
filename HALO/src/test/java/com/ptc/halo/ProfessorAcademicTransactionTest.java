package com.ptc.halo;

import com.ptc.halo.dtoRequest.*;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.open-in-view=false"},showSql=false)
@Import({ProfessorAcademicService.class,ActivityLogService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ProfessorAcademicTransactionTest {
 @Autowired ProfessorAcademicService service;
 @SpyBean ActivityLogService audit;
 @Autowired ActivityLogRepository logs;
 @Autowired SubjectRepository subjects;
 @SpyBean WeekRepository weeks;
 @Autowired AiLearningModuleRepository modules;
 @Autowired UserRepository users;
 @Autowired ProfessorRepository professors;
 UserEntity actor;
 @BeforeEach void setup() {
  reset(audit); reset(weeks);
  logs.deleteAll();modules.deleteAll();weeks.deleteAll();subjects.deleteAll();professors.deleteAll();users.deleteAll();
  actor=new UserEntity();actor.setName("Professor");actor.setEmail("transaction@example.test");actor.setRole(Role.PROFESSOR);actor.setStatus(Status.ACTIVE);actor=users.saveAndFlush(actor);
  var professor=new ProfessorEntity();professor.setUser(actor);professor.setProfessorId("TX");professors.saveAndFlush(professor);
 }
 SubjectRequest subject(String code) {var r=new SubjectRequest();r.setSubjectCode(code);r.setSubjectName(code);r.setYearLevel(YearLevel.FIRST_YEAR);return r;}
 SubjectUpdateRequest editSubject() {var r=new SubjectUpdateRequest();r.setSubjectCode("CHANGED");r.setSubjectName("Changed");r.setYearLevel(YearLevel.SECOND_YEAR);return r;}
 WeekRequest week() {var r=new WeekRequest();r.setWeekNumber(1);r.setTitle("Original week");return r;}
 WeekUpdateRequest editWeek() {var r=new WeekUpdateRequest();r.setWeekNumber(2);r.setTitle("Changed week");return r;}
 @Test void allSixSuccessfulMutationsCommitWithTheirAuditRecords() {
  assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
  Long subject=service.createSubject(subject("S"),actor).getId();assertTrue(subjects.existsById(subject));assertEquals(1,logs.count());
  service.updateSubject(subject,editSubject(),actor);assertEquals("CHANGED",subjects.findById(subject).orElseThrow().getSubjectCode());assertEquals(2,logs.count());
  Long week=service.createWeek(subject,week(),actor).getId();assertTrue(weeks.existsById(week));assertEquals(3,logs.count());
  service.updateWeek(week,editWeek(),actor);assertEquals("Changed week",weeks.findById(week).orElseThrow().getTitle());assertEquals(4,logs.count());
  service.deleteWeek(week,actor);assertFalse(weeks.existsById(week));assertEquals(5,logs.count());
  service.deleteSubject(subject,actor);assertFalse(subjects.existsById(subject));assertEquals(6,logs.count());
  assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
 }
 @ParameterizedTest
 @ValueSource(strings={"createSubject","updateSubject","deleteSubject","createWeek","updateWeek","deleteWeek"})
 void auditFailureRollsBackEachMutationAndAuditWrite(String operation) {
  Long subject=service.createSubject(subject("S"),actor).getId();
  Long week=operation.equals("updateWeek")||operation.equals("deleteWeek") ? service.createWeek(subject,week(),actor).getId() : null;
  long logCount=logs.count(),subjectCount=subjects.count(),weekCount=weeks.count();
  doAnswer(call->{
   assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
   call.callRealMethod(); // Real INSERT participates in the academic transaction.
   logs.flush();
   throw new IllegalStateException("forced audit failure");
  }).when(audit).createLog(any(),any(),anyString());
  var error=assertThrows(IllegalStateException.class,()->{
   switch(operation) {
    case "createSubject" -> service.createSubject(subject("NEW"),actor);
    case "updateSubject" -> service.updateSubject(subject,editSubject(),actor);
    case "deleteSubject" -> service.deleteSubject(subject,actor);
    case "createWeek" -> service.createWeek(subject,week(),actor);
    case "updateWeek" -> service.updateWeek(week,editWeek(),actor);
    case "deleteWeek" -> service.deleteWeek(week,actor);
    default -> fail("Unknown operation");
   }
  });
  assertEquals("forced audit failure",error.getMessage());
  assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
  assertEquals(logCount,logs.count());assertEquals(subjectCount,subjects.count());assertEquals(weekCount,weeks.count());
  assertEquals("S",subjects.findById(subject).orElseThrow().getSubjectCode());
  if(week!=null) {assertEquals("Original week",weeks.findById(week).orElseThrow().getTitle());assertEquals(1,weeks.findById(week).orElseThrow().getWeekNumber());}
 }

 @Test void subjectWithWeekOrModuleIsRejectedWithoutChangingRecordsOrAudit() {
  Long subject=service.createSubject(subject("S"),actor).getId();
  Long week=service.createWeek(subject,week(),actor).getId();
  long auditCount=logs.count();
  var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.deleteSubject(subject,actor));
  assertEquals(409,error.getStatusCode().value());assertEquals("SUBJECT_HAS_CONTENT",error.getReason());
  assertTrue(subjects.existsById(subject));assertTrue(weeks.existsById(week));assertEquals(auditCount,logs.count());
  var module=new AiLearningModuleEntity();module.setWeek(weeks.findById(week).orElseThrow());module.setStatus(LessonStatus.PENDING);module.setAiGenerationStatus(AiGenerationStatus.PENDING);module=modules.saveAndFlush(module);
  error=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.deleteSubject(subject,actor));
  assertEquals(409,error.getStatusCode().value());assertEquals("SUBJECT_HAS_CONTENT",error.getReason());
  assertTrue(subjects.existsById(subject));assertTrue(weeks.existsById(week));assertTrue(modules.existsById(module.getId()));assertEquals(auditCount,logs.count());
  var other=new UserEntity();other.setId(Long.MAX_VALUE);
  error=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.deleteSubject(subject,other));
  assertEquals(404,error.getStatusCode().value());assertEquals("RESOURCE_NOT_FOUND",error.getReason());
 }

 @Test void weekNumbersAreUniquePerSubjectWithSafeUpdates() {
  Long subject=service.createSubject(subject("S"),actor).getId();
  Long first=service.createWeek(subject,week(),actor).getId();
  long count=logs.count();
  var conflict=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.createWeek(subject,week(),actor));
  assertEquals(409,conflict.getStatusCode().value());assertEquals("WEEK_NUMBER_ALREADY_EXISTS",conflict.getReason());assertEquals(count,logs.count());
  doReturn(false).when(weeks).existsBySubject_IdAndWeekNumber(subject,1);
  conflict=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.createWeek(subject,week(),actor));
  assertEquals("WEEK_NUMBER_ALREADY_EXISTS",conflict.getReason()); // DB constraint race fallback.
  reset(weeks);
  Long other=service.createSubject(subject("OTHER"),actor).getId();assertNotNull(service.createWeek(other,week(),actor));
  var keep=new WeekUpdateRequest();keep.setWeekNumber(1);keep.setTitle("Renamed");service.updateWeek(first,keep,actor);
  var secondRequest=week();secondRequest.setWeekNumber(2);Long second=service.createWeek(subject,secondRequest,actor).getId();
  conflict=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.updateWeek(second,keep,actor));
  assertEquals("WEEK_NUMBER_ALREADY_EXISTS",conflict.getReason());assertEquals(2,weeks.findById(second).orElseThrow().getWeekNumber());
  var direct=new WeekEntity();direct.setSubject(subjects.findById(subject).orElseThrow());direct.setWeekNumber(1);direct.setTitle("Direct duplicate");
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->weeks.saveAndFlush(direct));
  assertEquals(3,weeks.count());
 }
}
