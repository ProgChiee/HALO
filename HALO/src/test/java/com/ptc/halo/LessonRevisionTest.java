package com.ptc.halo;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import com.ptc.halo.component.ModuleMaterialIndex;
import com.ptc.halo.dtoRequest.AiLearningModuleUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties="spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",showSql=false)
@Import({LessonRevisionService.class,ProfessorModuleService.class,ActivityLogService.class,ObjectMapper.class})
class LessonRevisionTest {
 @Autowired LessonRevisionService service;@Autowired UserRepository users;@Autowired ProfessorRepository professors;@Autowired SubjectRepository subjects;
 @Autowired WeekRepository weeks;@Autowired AiLearningModuleRepository modules;@Autowired LessonRevisionRepository revisions;@Autowired ActivityLogRepository logs;
 @Autowired StudentModuleProgressRepository progress;@Autowired jakarta.persistence.EntityManager em;
 @MockBean AssessmentContentChange changes;
 @MockBean FileUploadService uploads;@MockBean AiGenerationService generation;@MockBean AssessmentService assessments;@MockBean ModuleMaterialIndex index;
 UserEntity professor,student;AiLearningModuleEntity module;
 @BeforeEach void setup(){
  professor=user("Professor",Role.PROFESSOR);student=user("Student",Role.STUDENT);
  var p=new ProfessorEntity();p.setUser(professor);p.setProfessorId("P-REV");professors.saveAndFlush(p);
  var s=new SubjectEntity();s.setProfessor(p);s.setSubjectCode("REV");s.setSubjectName("Hospitality");s.setYearLevel(YearLevel.FIRST_YEAR);subjects.saveAndFlush(s);
  var w=new WeekEntity();w.setSubject(s);w.setTitle("Guest service");w.setWeekNumber(3);weeks.saveAndFlush(w);
  module=new AiLearningModuleEntity();module.setWeek(w);module.setLessonText("Published text");module.setAiNotes("Original notes");module.setStatus(LessonStatus.APPROVED);module.setAiGenerationStatus(AiGenerationStatus.COMPLETED);module.setGeneratedKnowledge("Published knowledge");
  var f=new AiLearningFileEntity();f.setOriginalFileName("Original.pdf");f.setStoredFileName("original");f.setFilePath("original");f.setFileType("application/pdf");module.addFile(f);modules.saveAndFlush(module);
  var history=new StudentModuleProgressEntity();history.setStudent(student);history.setModule(module);history.setCompleted(true);progress.saveAndFlush(history);
  when(index.buildRevision(any())).thenAnswer(i->new ModuleMaterialIndex.Index(module.getId(),"reviewed",List.of()));
  when(generation.generateRevision(any())).thenAnswer(i->{AiLearningModuleEntity c=i.getArgument(0);c.setAiGenerationStatus(AiGenerationStatus.COMPLETED);c.setGeneratedObjectives("New objectives");c.setGeneratedKnowledge("New knowledge");c.setGeneratedExamples("New examples");c.setGeneratedSummary("New summary");return c;});
 }
 UserEntity user(String name,Role role){var u=new UserEntity();u.setName(name);u.setEmail(name+"@test");u.setRole(role);u.setStatus(Status.ACTIVE);return users.saveAndFlush(u);}
 AiLearningModuleUpdateRequest values(){var v=new AiLearningModuleUpdateRequest();v.setLessonText("Revision text");v.setAiNotes("New notes");v.setYoutubeLink("https://youtu.be/dQw4w9WgXcQ");return v;}
 LessonRevisionService.View start(){return service.start(module.getId(),professor,null);}
 LessonRevisionService.View save(LessonRevisionService.View r)throws Exception{return service.save(module.getId(),r.version(),values(),r.files().stream().map(LessonRevisionService.FileView::key).toList(),List.of(),professor,null);}
 @Test void draftAndReviewNeverChangePublishedMaterialsAndPublishPreservesHistory()throws Exception{
  var assessment=new AssessmentEntity();assessment.setModule(module);assessment.setTitle("Existing assessment");assessment.setPassingScore(75);assessment.setStatus(AssessmentStatus.AVAILABLE);assessment.setCreatedAt(java.time.LocalDateTime.now());em.persist(assessment);
  var attempt=new AssessmentAttemptEntity();attempt.setAssessment(assessment);attempt.setStudent(student);attempt.setStartedAt(java.time.LocalDateTime.now());attempt.setScore(90);attempt.setPassed(true);em.persist(attempt);
  var mentor=new MentorSessionEntity();mentor.setStudent(student);mentor.setModule(module);mentor.setStartedAt(java.time.LocalDateTime.now());mentor.setLastActivityAt(java.time.LocalDateTime.now());mentor.setProgressStatus(MentorProgressStatus.LEARNING);em.persist(mentor);
  var message=new MentorMessageEntity();message.setSession(mentor);message.setSender(MessageSender.STUDENT);message.setMessage("Existing history");message.setCreatedAt(java.time.LocalDateTime.now());em.persist(message);em.flush();
  var r=start();assertNotNull(r.version());assertEquals("Published text",r.lessonText());assertEquals(1,r.files().size());
  r=save(r);assertEquals("DRAFT",r.state());assertEquals("Published text",module.getLessonText());assertEquals(LessonStatus.APPROVED,module.getStatus());verifyNoInteractions(index);
  var prepared=service.generate(service.prepare(module.getId(),r.version(),professor,false));
  r=service.reviewed(module.getId(),prepared,professor,null,service.fingerprint(prepared));assertEquals("REVIEWED",r.state());
  assertEquals("Published knowledge",module.getGeneratedKnowledge());verify(index,never()).getOrBuild(any());verify(index,never()).invalidate(any());
  var publishing=service.prepare(module.getId(),r.version(),professor,true);service.publish(module.getId(),publishing,service.fingerprint(publishing),professor,null);
  assertTrue(module.isAssessmentNeedsReview());assertEquals("Revision text",module.getLessonText());assertEquals("New knowledge",module.getGeneratedKnowledge());assertEquals(LessonStatus.APPROVED,module.getStatus());assertEquals(1,progress.count());assertTrue(progress.findAll().get(0).getCompleted());
  assertEquals(90,em.find(AssessmentAttemptEntity.class,attempt.getId()).getScore());assertEquals("Existing history",em.find(MentorMessageEntity.class,message.getId()).getMessage());assertEquals(assessment.getId(),em.find(AssessmentAttemptEntity.class,attempt.getId()).getAssessment().getId());
  verifyNoInteractions(assessments);verify(index,never()).invalidate(any());
  TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCommit());verify(index).invalidate(module.getId());verify(index).getOrBuild(argThat(m->m.getLessonText().equals("Revision text")));
  assertNull(service.get(module.getId(),professor));assertNotNull(revisions.findById(module.getId()).orElseThrow().publishedAt);
 }
 @Test void replacedFileIsRemovedOnlyAtPublication()throws Exception{
  var oldId=module.getFiles().get(0).getId();var r=start();
  var upload=new FileUploadService.ValidatedUpload("Original.pdf","application/pdf",new byte[]{1});when(uploads.validateFiles(any())).thenReturn(List.of(upload));
  var f=new AiLearningFileEntity();f.setOriginalFileName("Original.pdf");f.setStoredFileName("replacement");f.setFilePath("replacement");f.setFileType("application/pdf");when(uploads.uploadValidated(eq(upload),isNull())).thenReturn(f);
  r=service.save(module.getId(),r.version(),values(),List.of(),List.of(),professor,null);
  assertEquals(oldId,module.getFiles().get(0).getId());var prepared=service.generate(service.prepare(module.getId(),r.version(),professor,false));
  r=service.reviewed(module.getId(),prepared,professor,null,service.fingerprint(prepared));var publication=service.prepare(module.getId(),r.version(),professor,true);service.publish(module.getId(),publication,service.fingerprint(publication),professor,null);
  em.flush();em.clear();var saved=modules.findWithFilesById(module.getId()).orElseThrow();assertEquals(1,saved.getFiles().size());assertNotEquals(oldId,saved.getFiles().get(0).getId());assertEquals("replacement",saved.getFiles().get(0).getFilePath());assertEquals(1,progress.count());
 }
 @Test void failuresAndStaleOperationsLeavePublishedContentIntact()throws Exception{
  var r=save(start());final var draft=r;
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.prepare(module.getId(),draft.version(),professor,true));
  doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,"MATERIAL_NOT_RELEVANT")).when(generation).generateRevision(any());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.generate(service.prepare(module.getId(),draft.version(),professor,false)));
  assertEquals("Published text",module.getLessonText());assertEquals("DRAFT",service.get(module.getId(),professor).state());
  var oldVersion=r.version();save(r);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.prepare(module.getId(),oldVersion,professor,false));
  verify(index,never()).invalidate(any());assertEquals(1,progress.count());
 }
 @Test void discardOwnershipAndActingAudit()throws Exception{
  var other=user("Other",Role.PROFESSOR);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.start(module.getId(),other,null));
  var r=start();service.discard(module.getId(),r.version(),professor,null);assertNull(service.get(module.getId(),professor));assertEquals("Published text",module.getLessonText());verify(index,never()).invalidate(any());
 }

 @Test void actingWritesRetainRealAdminAndTargetInAudit() {
  var admin=user("Admin",Role.ADMIN);var now=java.time.Instant.now();var acting=new AdminActingSessionEntity(UUID.randomUUID().toString(),admin,professor,now,now.plusSeconds(300));em.persist(acting);em.flush();
  service.start(module.getId(),professor,acting);em.flush();
  var entry=logs.findAll().get(0);assertEquals(admin.getId(),entry.getUser().getId());assertEquals(professor.getId(),entry.getActingTarget().getId());assertEquals(acting.getId(),entry.getActingSession().getId());
 }

 @Test void changedMaterialsCannotPublishWithoutFreshReview()throws Exception {
  var r=save(start());var prepared=service.generate(service.prepare(module.getId(),r.version(),professor,false));
  r=service.reviewed(module.getId(),prepared,professor,null,service.fingerprint(prepared));
  var publication=service.prepare(module.getId(),r.version(),professor,true);
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.publish(module.getId(),publication,"changed-file-fingerprint",professor,null));
  assertEquals("Published text",module.getLessonText());assertEquals(LessonStatus.APPROVED,module.getStatus());assertEquals(1,progress.count());verify(index,never()).invalidate(any());
 }

 @Test void youtubeOnlyPublicationKeepsGeneratedLessonAndDoesNotFlagQuiz()throws Exception{
  var quiz=new AssessmentEntity();quiz.setModule(module);quiz.setTitle("Quiz");quiz.setPassingScore(70);quiz.setStatus(AssessmentStatus.AVAILABLE);quiz.setCreatedAt(java.time.LocalDateTime.now());em.persist(quiz);em.flush();
  var r=start();var values=new AiLearningModuleUpdateRequest();values.setLessonText(module.getLessonText());values.setAiNotes(module.getAiNotes());values.setYoutubeLink("https://youtu.be/dQw4w9WgXcQ");
  r=service.save(module.getId(),r.version(),values,r.files().stream().map(LessonRevisionService.FileView::key).toList(),List.of(),professor,null);
  var prepared=service.generate(service.prepare(module.getId(),r.version(),professor,false));verifyNoInteractions(generation);
  r=service.reviewed(module.getId(),prepared,professor,null,service.fingerprint(prepared));
  var publishing=service.prepare(module.getId(),r.version(),professor,true);
  assertFalse(service.assessmentChanged(publishing));service.publish(module.getId(),publishing,service.fingerprint(publishing),professor,null,false);
  assertFalse(module.isAssessmentNeedsReview());assertEquals("Published knowledge",module.getGeneratedKnowledge());assertEquals(AssessmentStatus.AVAILABLE,quiz.getStatus());
 }
}
