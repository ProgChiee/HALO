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
@Import({LessonRevisionService.class,QuizRevisionService.class,ProfessorModuleService.class,ActivityLogService.class,ObjectMapper.class})
class LessonRevisionTest {
 @org.springframework.boot.test.mock.mockito.MockBean com.ptc.halo.service.LessonSourceScope sourceScopes;
 @Autowired LessonRevisionService service;@Autowired UserRepository users;@Autowired ProfessorRepository professors;@Autowired SubjectRepository subjects;
 @Autowired WeekRepository weeks;@Autowired AiLearningModuleRepository modules;@Autowired LessonRevisionRepository revisions;@Autowired ActivityLogRepository logs;
 @Autowired StudentModuleProgressRepository progress;@Autowired jakarta.persistence.EntityManager em;
 @Autowired LessonRevisionHistoryRepository history;
 @Autowired QuizRevisionService quizRevisions;
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
 @Test void expansionRevisionRequiresAcademicReviewAndPreservesImmutablePublishedBaseline()throws Exception{
  var storage=mock(LessonMaterialStorage.class);var worker=mock(ObtlExtractionProcess.class);when(storage.read("original")).thenReturn("fixture".getBytes());
  when(worker.extract(any())).thenReturn(new ObtlPdfText.Extracted("fixture",ObtlSections.detect(List.of(new ObtlSections.Page(1,"Chapter 1 Guest service\nTopics and outcomes\nChapter 2 EXCLUDED")),"Guest service")));
  var scopes=new LessonSourceScope(new ObjectMapper(),storage,worker);org.springframework.test.util.ReflectionTestUtils.setField(service,"sources",scopes);
  when(index.buildRevision(any())).thenAnswer(i->scopes.teachingIndex(i.getArgument(0),false));
  try{
   var r=start();var c=service.sourceCandidate(module.getId(),r.version(),professor);c.setSourceScope(scopes.extract(c));
   String id=scopes.read(c.getSourceScope()).documents().get(0).extraction().sections().get(0).id();
   c.setSourceScope(scopes.confirm(c,List.of(new LessonSourceScope.Choice(c.getFiles().get(0).getId(),List.of(id),false)),professor.getId(),LessonSourceScope.Mode.OBTL_EXPANSION));
   service.saveSource(module.getId(),r.version(),c.getSourceScope(),professor,null);r=service.get(module.getId(),professor);
   var prepared=service.prepare(module.getId(),r.version(),professor,false);c=prepared.candidate();new LessonSourceScope.Lesson("Objectives","Detailed instruction","Hospitality scenario","Summary").apply(c);scopes.markInstructionVerified(c);c.setAiGenerationStatus(AiGenerationStatus.COMPLETED);
   r=service.reviewed(module.getId(),prepared,professor,null,index.buildRevision(c).fingerprint());
   var unreviewed=service.prepare(module.getId(),r.version(),professor,true);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.publish(module.getId(),unreviewed,index.buildRevision(unreviewed.candidate()).fingerprint(),professor,null));
   assertEquals("Published knowledge",module.getGeneratedKnowledge());assertNull(module.getSourceScope());
   long oldVersion=r.version();c=service.sourceCandidate(module.getId(),r.version(),professor);scopes.acknowledgeExpansion(c,professor.getId());service.saveExpansionReview(module.getId(),r.version(),c,professor,null);
   final var reviewedCandidate=c;assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.saveExpansionReview(module.getId(),oldVersion,reviewedCandidate,professor,null));
   r=service.get(module.getId(),professor);var publish=service.prepare(module.getId(),r.version(),professor,true);service.publish(module.getId(),publish,index.buildRevision(publish.candidate()).fingerprint(),professor,null);em.flush();
   String historical=history.findByModuleIdAndRevisionNumber(module.getId(),1).orElseThrow().getPayload();assertTrue(historical.contains("OBTL_EXPANSION"));assertNotNull(scopes.read(module.getSourceScope()).review());
   assertTrue(history.findByModuleIdAndRevisionNumber(module.getId(),0).orElseThrow().getPayload().contains("Published knowledge"));
   var next=start();service.saveSource(module.getId(),next.version(),scopes.extract(service.sourceCandidate(module.getId(),next.version(),professor)),professor,null);
   assertEquals(historical,history.findByModuleIdAndRevisionNumber(module.getId(),1).orElseThrow().getPayload());assertEquals("Detailed instruction",module.getGeneratedKnowledge());
  }finally{org.springframework.test.util.ReflectionTestUtils.setField(service,"sources",sourceScopes);}
 }
 @Test void confirmedOutlineToInstructionalPublicationKeepsExactSourceIsolation()throws Exception{
  var storage=mock(LessonMaterialStorage.class);var worker=mock(ObtlExtractionProcess.class);
  when(storage.read("original")).thenReturn("synthetic outline identity".getBytes());
  when(storage.read("instruction")).thenReturn("synthetic instructional identity".getBytes());
  when(worker.extract(any())).thenAnswer(i->{
   boolean outline=Arrays.equals(i.getArgument(0),"synthetic outline identity".getBytes());
   String text=outline?"Chapter 1 Management\nWhat is management?\nChapter 2 Excluded\nEXCLUDED_CHAPTER_TWO":"Chapter 1 Management\nManagers plan work and coordinate staff.\nChapter 2 Excluded\nEXCLUDED_SUPPLEMENT";
   return new ObtlPdfText.Extracted("worker-test",ObtlSections.detect(List.of(new ObtlSections.Page(1,text)),"Management"));
  });
  var f=new AiLearningFileEntity();f.setOriginalFileName("Instruction.pdf");f.setStoredFileName("instruction");f.setFilePath("instruction");f.setFileType("application/pdf");module.addFile(f);modules.saveAndFlush(module);
  var realScope=new LessonSourceScope(new ObjectMapper(),storage,worker);
  var chat=mock(org.springframework.ai.chat.client.ChatClient.class,RETURNS_DEEP_STUBS);
  var builder=mock(org.springframework.ai.chat.client.ChatClient.Builder.class);when(builder.build()).thenReturn(chat);
  var realGeneration=new AiGenerationService(builder,modules,new ObjectMapper(),index,mock(ModuleGenerationState.class),storage);
  org.springframework.test.util.ReflectionTestUtils.setField(realGeneration,"sourceScopes",realScope);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"sources",realScope);
  try{
   var r=start();var candidate=service.sourceCandidate(module.getId(),r.version(),professor);
   service.saveSource(module.getId(),r.version(),realScope.extract(candidate),professor,null);
   r=service.get(module.getId(),professor);candidate=service.sourceCandidate(module.getId(),r.version(),professor);
   var documents=realScope.read(candidate.getSourceScope()).documents();var outline=documents.get(0);var instruction=documents.get(1);
   service.saveSource(module.getId(),r.version(),realScope.confirm(candidate,List.of(new LessonSourceScope.Choice(outline.fileId(),List.of(outline.extraction().sections().get(0).id()),false)),professor.getId()),professor,null);
   r=service.get(module.getId(),professor);var prepared=service.prepare(module.getId(),r.version(),professor,false);
   realGeneration.generateRevision(prepared.candidate());r=service.reviewed(module.getId(),prepared,professor,null,realScope.index(prepared.candidate(),true).fingerprint());
   assertEquals("DRAFT",r.state());assertNull(module.getSourceScope());verifyNoInteractions(chat,assessments);
   final long draftVersion=r.version();assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.prepare(module.getId(),draftVersion,professor,true));
   candidate=service.sourceCandidate(module.getId(),r.version(),professor);
   service.saveSource(module.getId(),r.version(),realScope.confirm(candidate,List.of(new LessonSourceScope.Choice(outline.fileId(),List.of(outline.extraction().sections().get(0).id()),false),new LessonSourceScope.Choice(instruction.fileId(),List.of(instruction.extraction().sections().get(0).id()),true)),professor.getId()),professor,null);
   r=service.get(module.getId(),professor);prepared=service.prepare(module.getId(),r.version(),professor,false);
   String sourceId=realScope.index(prepared.candidate(),true).chunks().get(0).id();
   when(chat.prompt().system(startsWith("Evaluate only")).user(anyString()).call().content()).thenReturn("{\"relevant\":true,\"sufficient\":true}");
   when(chat.prompt().system(startsWith("Generate a lesson ONLY")).user(anyString()).call().content()).thenReturn(new ObjectMapper().writeValueAsString(Map.of("valid",true,"objectives","Learn management","knowledge","Managers plan work and coordinate staff.","examples","Coordinating staff","summary","Plan and coordinate","sourceIds",List.of(sourceId))));
   realGeneration.generateRevision(prepared.candidate());String fingerprint=realScope.index(prepared.candidate(),true).fingerprint();
   r=service.reviewed(module.getId(),prepared,professor,null,fingerprint);assertEquals("REVIEWED",r.state());assertNull(module.getSourceScope());
   var publication=service.prepare(module.getId(),r.version(),professor,true);service.publish(module.getId(),publication,realScope.index(publication.candidate(),true).fingerprint(),professor,null);em.flush();
   realScope.requirePublishedReady(module);var published=realScope.index(module,true);
   assertEquals(1,published.chunks().size());assertFalse(published.chunks().get(0).text().contains("EXCLUDED"));
   assertTrue(published.chunks().get(0).text().contains("coordinate staff"));
   assertTrue(history.findByModuleIdAndRevisionNumber(module.getId(),1).orElseThrow().getPayload().contains(sourceId.substring(0,36)));
   assertTrue(history.findByModuleIdAndRevisionNumber(module.getId(),0).orElseThrow().getPayload().contains("Published text"));
  }finally{org.springframework.test.util.ReflectionTestUtils.setField(service,"sources",sourceScopes);}
 }
 @Test void sourceSelectionChangesOnlyDraftAndRejectsStaleEdits()throws Exception{
  var r=start();String baseline=history.findByModuleIdAndRevisionNumber(module.getId(),0).orElseThrow().getPayload();
  service.saveSource(module.getId(),r.version(),"synthetic-snapshot",professor,null);
  em.flush();em.clear();
  assertNull(modules.findById(module.getId()).orElseThrow().getSourceScope());
  var next=service.get(module.getId(),professor);assertEquals("DRAFT",next.state());
  assertEquals("synthetic-snapshot",service.sourceCandidate(module.getId(),next.version(),professor).getSourceScope());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.saveSource(module.getId(),r.version(),"stale",professor,null));
  assertEquals(baseline,history.findByModuleIdAndRevisionNumber(module.getId(),0).orElseThrow().getPayload());
 }
 @Test void outlineRevisionStaysDraftAndCannotPublishOrGenerateAssessment(){
  var r=start();service.saveSource(module.getId(),r.version(),"outline-snapshot",professor,null);
  r=service.get(module.getId(),professor);
  doAnswer(i->{AiLearningModuleEntity c=i.getArgument(0);c.setAiGenerationStatus(AiGenerationStatus.PENDING);c.setGeneratedKnowledge("Outline plan");return c;}).when(generation).generateRevision(any());
  var reviewed=review(r);assertEquals("DRAFT",reviewed.state());assertEquals("Outline plan",reviewed.generatedKnowledge());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.prepare(module.getId(),reviewed.version(),professor,true));
  assertNull(module.getSourceScope());assertEquals("Published text",module.getLessonText());verifyNoInteractions(assessments);
 }
 @Test void publishedSourceSnapshotIsRetainedInImmutableHistory(){
  var r=start();service.saveSource(module.getId(),r.version(),"confirmed-instructional-snapshot",professor,null);
  r=review(service.get(module.getId(),professor));publish(r);em.flush();
  String snapshot=history.findByModuleIdAndRevisionNumber(module.getId(),1).orElseThrow().getPayload();
  assertTrue(snapshot.contains("confirmed-instructional-snapshot"));assertEquals("confirmed-instructional-snapshot",module.getSourceScope());
  var next=start();service.saveSource(module.getId(),next.version(),"changed-draft",professor,null);
  assertEquals(snapshot,history.findByModuleIdAndRevisionNumber(module.getId(),1).orElseThrow().getPayload());
  assertEquals("confirmed-instructional-snapshot",module.getSourceScope());verify(sourceScopes).requirePublishedReady(any());
 }
 LessonRevisionService.View save(LessonRevisionService.View r)throws Exception{return service.save(module.getId(),r.version(),values(),r.files().stream().map(LessonRevisionService.FileView::key).toList(),List.of(),professor,null);}
 AssessmentEntity quizDraft(){
  var quiz=new AssessmentEntity();quiz.setModule(module);quiz.setTitle("Replacement quiz");quiz.setQuizVersion(2);quiz.setPassingScore(75);quiz.setStatus(AssessmentStatus.DRAFT);quiz.setCreatedAt(java.time.LocalDateTime.now());quiz.setSourceModuleVersion(module.getVersion());em.persist(quiz);em.flush();return quiz;
 }
 @Test void quizApprovalLeavesExistingLessonDraftUsable()throws Exception{
  module.setAssessmentNeedsReview(true);modules.saveAndFlush(module);
  var r=start();var baseVersion=module.getVersion();var quiz=quizDraft();
  quizRevisions.approve(module.getId(),quiz.getId(),professor,null);
  assertEquals(baseVersion+1,module.getVersion());assertFalse(module.isAssessmentNeedsReview());
  assertEquals(r.version(),service.get(module.getId(),professor).version());
  var saved=save(r);var reviewed=review(saved);publish(reviewed);
  assertEquals("Revision text",module.getLessonText());
 }
 @Test void staleLessonDraftCanBeDiscardedWithoutOverwritingLiveContent()throws Exception{
  var r=start();module.setLessonText("Newer published content");modules.saveAndFlush(module);
  service.discard(module.getId(),r.version(),professor,null);
  assertNull(service.get(module.getId(),professor));assertEquals("Newer published content",module.getLessonText());
  verify(uploads,never()).deleteFile("original");
 }
 @Test void quizApprovalDuringReviewKeepsPreparedDraftValid()throws Exception{
  module.setAssessmentNeedsReview(true);modules.saveAndFlush(module);
  var r=save(start());var prepared=service.generate(service.prepare(module.getId(),r.version(),professor,false));
  var quiz=quizDraft();quizRevisions.approve(module.getId(),quiz.getId(),professor,null);
  var reviewed=service.reviewed(module.getId(),prepared,professor,null,service.fingerprint(prepared));
  assertEquals("REVIEWED",reviewed.state());assertEquals("Published text",module.getLessonText());
 }
 @Test void quizApprovalDuringPublicationPreservesSnapshotAndOldAttempts()throws Exception{
  module.setAssessmentNeedsReview(true);modules.saveAndFlush(module);
  var old=new AssessmentEntity();old.setModule(module);old.setTitle("Original quiz");old.setQuizVersion(1);old.setPassingScore(75);old.setStatus(AssessmentStatus.AVAILABLE);old.setCreatedAt(java.time.LocalDateTime.now());em.persist(old);
  var completed=new AssessmentAttemptEntity();completed.setAssessment(old);completed.setStudent(student);completed.setStartedAt(java.time.LocalDateTime.now());completed.setSubmittedAt(java.time.LocalDateTime.now());completed.setScore(90);completed.setPassed(true);em.persist(completed);
  var unfinished=new AssessmentAttemptEntity();unfinished.setAssessment(old);unfinished.setStudent(student);unfinished.setStartedAt(java.time.LocalDateTime.now());unfinished.setScore(0);unfinished.setPassed(false);em.persist(unfinished);em.flush();
  var r=review(save(start()));var prepared=service.prepare(module.getId(),r.version(),professor,true);
  String payloadBefore=revisions.findById(module.getId()).orElseThrow().payload;
  var baseline=history.findByModuleIdAndRevisionNumber(module.getId(),0).orElseThrow();String baselinePayload=baseline.getPayload();
  var quiz=quizDraft();quizRevisions.approve(module.getId(),quiz.getId(),professor,null);
  em.clear();
  var draft=revisions.findById(module.getId()).orElseThrow();assertEquals(r.version(),draft.version);assertEquals(payloadBefore,draft.payload);assertEquals("REVIEWED",draft.state);
  assertEquals(modules.findById(module.getId()).orElseThrow().getVersion(),draft.baseVersion);
  service.publish(module.getId(),prepared,service.fingerprint(prepared),professor,null);
  em.flush();em.clear();
  assertEquals(baselinePayload,history.findByModuleIdAndRevisionNumber(module.getId(),0).orElseThrow().getPayload());
  assertEquals("Revision text",service.historyVersion(module.getId(),1,professor).lessonText());
  var saved=em.find(AssessmentAttemptEntity.class,completed.getId());assertEquals(90,saved.getScore());assertTrue(saved.getPassed());assertEquals(old.getId(),saved.getAssessment().getId());
  var activeAttempt=em.find(AssessmentAttemptEntity.class,unfinished.getId());assertNull(activeAttempt.getSubmittedAt());assertEquals(old.getId(),activeAttempt.getAssessment().getId());
  assertEquals(AssessmentStatus.RETIRED,em.find(AssessmentEntity.class,old.getId()).getStatus());assertEquals(AssessmentStatus.AVAILABLE,em.find(AssessmentEntity.class,quiz.getId()).getStatus());
  assertEquals(1,progress.count());
 }
 void assertStale(org.junit.jupiter.api.function.Executable action){
  var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,action);
  assertEquals(409,error.getStatusCode().value());assertEquals("STALE_MODULE_OPERATION",error.getReason());
 }
 @Test void quizApprovalDoesNotRebaseAnAlreadyConflictingLessonDraft()throws Exception{
  module.setAssessmentNeedsReview(true);modules.saveAndFlush(module);var r=save(start());
  module.setLessonText("New published lesson");modules.saveAndFlush(module);
  var quiz=quizDraft();quizRevisions.approve(module.getId(),quiz.getId(),professor,null);
  assertEquals(r.baseVersion(),revisions.findById(module.getId()).orElseThrow().baseVersion);
  assertStale(()->save(r));assertStale(()->service.prepare(module.getId(),r.version(),professor,false));
  assertEquals("New published lesson",module.getLessonText());
 }
 @Test void realLessonChangesDuringReviewOrPublicationStillConflict()throws Exception{
  var r=save(start());var reviewing=service.generate(service.prepare(module.getId(),r.version(),professor,false));
  var reviewed=service.reviewed(module.getId(),reviewing,professor,null,service.fingerprint(reviewing));
  var publishing=service.prepare(module.getId(),reviewed.version(),professor,true);
  module.setGeneratedKnowledge("Newer approved knowledge");modules.saveAndFlush(module);
  assertStale(()->service.reviewed(module.getId(),publishing,professor,null,service.fingerprint(publishing)));
  assertStale(()->service.publish(module.getId(),publishing,service.fingerprint(publishing),professor,null));
  assertEquals("Newer approved knowledge",module.getGeneratedKnowledge());assertEquals("Published text",module.getLessonText());
  assertFalse(history.existsByModuleIdAndRevisionNumber(module.getId(),1));
 }
 @Test void discardAfterQuizApprovalStillRejectsStaleClientAndOtherProfessors()throws Exception{
  module.setAssessmentNeedsReview(true);modules.saveAndFlush(module);var first=start();var current=save(first);
  var quiz=quizDraft();quizRevisions.approve(module.getId(),quiz.getId(),professor,null);
  assertStale(()->service.discard(module.getId(),first.version(),professor,null));
  var other=user("Unrelated",Role.PROFESSOR);
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.discard(module.getId(),current.version(),other,null));
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.save(module.getId(),current.version(),values(),List.of(),List.of(),other,null));
  assertEquals("Revision text",service.get(module.getId(),professor).lessonText());
  service.discard(module.getId(),current.version(),professor,null);
  assertNull(service.get(module.getId(),professor));assertEquals("Published text",module.getLessonText());
 }
 @Test void actingQuizApprovalAndStaleDiscardKeepRealAdminAudit()throws Exception{
  module.setAssessmentNeedsReview(true);modules.saveAndFlush(module);var r=start();
  var admin=user("SupportAdmin",Role.ADMIN);var now=java.time.Instant.now();var acting=new AdminActingSessionEntity(UUID.randomUUID().toString(),admin,professor,now,now.plusSeconds(300));em.persist(acting);em.flush();
  var quiz=quizDraft();quizRevisions.approve(module.getId(),quiz.getId(),professor,acting);
  module.setLessonText("Newer lesson");modules.saveAndFlush(module);
  service.discard(module.getId(),r.version(),professor,acting);
  var audited=logs.findAll().stream().filter(l->l.getActingSession()!=null).toList();assertEquals(2,audited.size());
  for(var log:audited){assertEquals(admin.getId(),log.getUser().getId());assertEquals(professor.getId(),log.getActingTarget().getId());assertEquals(acting.getId(),log.getActingSession().getId());}
 }
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

 LessonRevisionService.View review(LessonRevisionService.View r){
  var prepared=service.generate(service.prepare(module.getId(),r.version(),professor,false));
  return service.reviewed(module.getId(),prepared,professor,null,service.fingerprint(prepared));
 }
 void publish(LessonRevisionService.View r){
  var prepared=service.prepare(module.getId(),r.version(),professor,true);
  service.publish(module.getId(),prepared,service.fingerprint(prepared),professor,null);
 }
 @Test void publishedSnapshotsSurviveNextDraftReviewPublicationAndDiscard()throws Exception{
  var quiz=new AssessmentEntity();quiz.setModule(module);quiz.setTitle("Historical quiz");quiz.setQuizVersion(1);quiz.setPassingScore(75);quiz.setStatus(AssessmentStatus.AVAILABLE);quiz.setCreatedAt(java.time.LocalDateTime.now());em.persist(quiz);
  var attempt=new AssessmentAttemptEntity();attempt.setAssessment(quiz);attempt.setStudent(student);attempt.setStartedAt(java.time.LocalDateTime.now());attempt.setSubmittedAt(java.time.LocalDateTime.now());attempt.setScore(90);attempt.setPassed(true);em.persist(attempt);em.flush();
  var first=review(save(start()));publish(first);
  var original=history.findByModuleIdAndRevisionNumber(module.getId(),1).orElseThrow();
  em.detach(original); // Compare persisted timestamp precision before and after later revisions.
  original=history.findByModuleIdAndRevisionNumber(module.getId(),1).orElseThrow();
  String exactPayload=original.getPayload();var metadata=service.historyVersion(module.getId(),1,professor).metadata();
  var baseline=service.historyVersion(module.getId(),0,professor);
  assertEquals("INITIAL_PUBLISHED",baseline.metadata().snapshotKind());assertEquals("Published text",baseline.lessonText());
  assertNull(baseline.metadata().publishedAt());assertNotNull(baseline.metadata().recordedAt());
  var second=start();assertEquals(2,second.revisionNumber());assertNull(second.publishedAt());
  assertNull(revisions.findById(module.getId()).orElseThrow().publishedBy);
  var values=values();values.setLessonText("Second revision content");values.setAiNotes("Second revision notes");
  second=service.save(module.getId(),second.version(),values,second.files().stream().map(LessonRevisionService.FileView::key).toList(),List.of(),professor,null);
  assertEquals("Revision text",modules.findWithFilesById(module.getId()).orElseThrow().getLessonText());
  var candidate=service.prepare(module.getId(),second.version(),professor,false);
  assertEquals("Second revision content",candidate.candidate().getLessonText());
  assertEquals("Revision text",service.historyVersion(module.getId(),1,professor).lessonText());
  clearInvocations(index);second=review(second);
  verify(index,never()).getOrBuild(any());verify(index,never()).invalidate(any());
  assertEquals("Revision text",module.getLessonText());
  assertEquals(exactPayload,history.findByModuleIdAndRevisionNumber(module.getId(),1).orElseThrow().getPayload());
  publish(second);em.flush();em.clear();
  assertEquals("Second revision content",modules.findWithFilesById(module.getId()).orElseThrow().getLessonText());
  assertEquals(exactPayload,history.findByModuleIdAndRevisionNumber(module.getId(),1).orElseThrow().getPayload());
  assertEquals(metadata,service.historyVersion(module.getId(),1,professor).metadata());
  assertEquals("Second revision content",service.historyVersion(module.getId(),2,professor).lessonText());
  assertEquals(List.of(2,1,0),service.history(module.getId(),professor,0,20).stream().map(LessonRevisionService.HistorySummary::revisionNumber).toList());
  assertEquals(3,service.history(module.getId(),professor,0,1).getTotalElements());
  assertEquals(100,service.history(module.getId(),professor,0,500).getSize());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.history(module.getId(),professor,-1,20));
  var third=start();service.discard(module.getId(),third.version(),professor,null);em.flush();em.clear();
  assertEquals(3,service.history(module.getId(),professor,0,20).getTotalElements());
  assertEquals(metadata,service.historyVersion(module.getId(),1,professor).metadata());
  assertEquals(exactPayload,history.findByModuleIdAndRevisionNumber(module.getId(),1).orElseThrow().getPayload());
  assertEquals(1,progress.count());verify(uploads,never()).deleteFile("original");
  var savedAttempt=em.find(AssessmentAttemptEntity.class,attempt.getId());assertEquals(90,savedAttempt.getScore());assertTrue(savedAttempt.getPassed());
  assertEquals(quiz.getId(),savedAttempt.getAssessment().getId());assertEquals(1,savedAttempt.getAssessment().getQuizVersion());
  assertEquals(AssessmentStatus.AVAILABLE,savedAttempt.getAssessment().getStatus());verifyNoInteractions(assessments);
 }
 @Test void intactLegacyPublicationIsArchivedBeforeWorkingSlotIsReused()throws Exception{
  // Simulate a publication made by the old code, without a history row.
  var r=new LessonRevisionEntity();r.moduleId=module.getId();r.baseVersion=module.getVersion();r.revisionNumber=1;r.state="PUBLISHED";
  r.revisedBy=professor.getId();r.publishedBy=professor.getId();r.revisedAt=java.time.LocalDateTime.now().minusDays(2);r.publishedAt=java.time.LocalDateTime.now().minusDays(1);
  r.payload=new ObjectMapper().writeValueAsString(new LessonRevisionService.Payload("Legacy text",null,"Legacy notes",List.of(),"Objectives","Knowledge","Examples","Summary","fingerprint"));
  revisions.saveAndFlush(r);String exact=r.payload;var publishedAt=r.publishedAt;var version=r.version;
  var next=start();assertEquals(2,next.revisionNumber());
  var snapshot=history.findByModuleIdAndRevisionNumber(module.getId(),1).orElseThrow();
  assertEquals(exact,snapshot.getPayload());assertEquals(publishedAt,snapshot.getPublishedAt());assertEquals(version,snapshot.getRevisionVersion());
  assertNull(snapshot.getPublishedModuleVersion());assertEquals("Legacy text",service.historyVersion(module.getId(),1,professor).lessonText());
  assertFalse(history.existsByModuleIdAndRevisionNumber(module.getId(),0));
 }
 @Test void alreadyOverwrittenLegacyHistoryIsNotInvented() {
  var r=new LessonRevisionEntity();r.moduleId=module.getId();r.baseVersion=module.getVersion();r.revisionNumber=2;r.state="DRAFT";
  r.revisedBy=professor.getId();r.revisedAt=java.time.LocalDateTime.now();r.payload="{\"files\":[]}";revisions.saveAndFlush(r);
  assertEquals(2,start().revisionNumber());assertEquals(1,service.history(module.getId(),professor,0,20).getTotalElements());
  var baseline=service.historyVersion(module.getId(),0,professor);assertEquals("LEGACY_LIVE_BASELINE",baseline.metadata().snapshotKind());
  assertEquals("Published text",baseline.lessonText());assertEquals("Published knowledge",baseline.generatedKnowledge());
  assertNull(baseline.metadata().publishedAt());assertNull(baseline.metadata().revisedBy());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.historyVersion(module.getId(),1,professor));
 }
 @Test void historyRequiresModuleOwnershipAndExposesNoPhysicalStoragePaths()throws Exception{
  publish(review(save(start())));
  var other=user("OtherProfessor",Role.PROFESSOR);
  for(var actor:List.of(other,student,user("AdminHistory",Role.ADMIN))) {
   assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.history(module.getId(),actor,0,20));
   assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.historyVersion(module.getId(),1,actor));
  }
  String response=new ObjectMapper().findAndRegisterModules().writeValueAsString(service.historyVersion(module.getId(),1,professor));
  assertFalse(response.contains("filePath"));assertFalse(response.contains("storedFileName"));
  assertFalse(Arrays.stream(LessonRevisionHistoryRepository.class.getMethods()).anyMatch(m->m.getName().startsWith("delete")||m.getName().equals("save")));
 }
 @Test void removingAPublishedSnapshotThroughJpaIsRejected()throws Exception{
  publish(review(save(start())));
  var snapshot=history.findByModuleIdAndRevisionNumber(module.getId(),1).orElseThrow();
  assertThrows(IllegalStateException.class,()->em.remove(snapshot));
 }
}
