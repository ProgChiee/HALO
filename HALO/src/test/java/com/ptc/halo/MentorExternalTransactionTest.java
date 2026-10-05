package com.ptc.halo;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import com.ptc.halo.dtoRequest.StudentAnswerRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.open-in-view=false"},showSql=false)
@Import({MentorService.class,com.fasterxml.jackson.databind.ObjectMapper.class,AssessmentService.class,BadgeService.class,StudentLearningProgressionService.class,StudentLessonAccessService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class MentorExternalTransactionTest {
 @Autowired AssessmentService service;
 @Autowired MentorService mentor;
 @Autowired MentorSessionRepository sessions;
 @Autowired MentorMessageRepository messages;
 @MockBean com.ptc.halo.component.ModuleMaterialIndex index;
 @MockBean(answer=org.mockito.Answers.RETURNS_DEEP_STUBS, reset=org.springframework.boot.test.mock.mockito.MockReset.NONE) org.springframework.ai.chat.client.ChatClient.Builder builder;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
 @Autowired UserRepository users;
 @Autowired SubjectRepository subjects;
 @Autowired WeekRepository weeks;
 @Autowired AiLearningModuleRepository modules;
 @Autowired AssessmentRepository assessments;
 @Autowired AssessmentQuestionRepository questions;
 @Autowired AssessmentAttemptRepository attempts;
 @Autowired StudentAnswerRepository answers;
 @Autowired StudentModuleProgressRepository progress;
 @Autowired StudentBadgeRepository badges;
 @MockBean AssessmentAiService ai;
 @Autowired StudentProfileRepository profiles;
 @MockBean ActivityLogService audit;
 UserEntity student;AssessmentAttemptEntity attempt;AssessmentQuestionEntity q1,q2,foreign;
 @BeforeEach void setup(){
  reset(audit);messages.deleteAll();sessions.deleteAll();badges.deleteAll();progress.deleteAll();answers.deleteAll();attempts.deleteAll();questions.deleteAll();assessments.deleteAll();modules.deleteAll();weeks.deleteAll();subjects.deleteAll();profiles.deleteAll();users.deleteAll();
  student=new UserEntity();student.setEmail("student@test.example");student.setName("Student");student.setRole(Role.STUDENT);student.setStatus(Status.ACTIVE);student=users.saveAndFlush(student);
  var subject=new SubjectEntity();subject.setYearLevel(YearLevel.FIRST_YEAR);subject.setSubjectCode("S");subject.setSubjectName("Subject");subject=subjects.saveAndFlush(subject);
  var week=new WeekEntity();week.setSubject(subject);week.setWeekNumber(1);week.setTitle("Week");week=weeks.saveAndFlush(week);
  var module=new AiLearningModuleEntity();module.setWeek(week);module.setAiGenerationStatus(AiGenerationStatus.COMPLETED);module.setStatus(LessonStatus.APPROVED);module=modules.saveAndFlush(module);
  var assessment=new AssessmentEntity();assessment.setModule(module);assessment.setTitle("Quiz");assessment.setPassingScore(70);assessment.setStatus(AssessmentStatus.AVAILABLE);assessment.setCreatedAt(LocalDateTime.now());assessment=assessments.saveAndFlush(assessment);
  q1=question(assessment,1,"A");q2=question(assessment,2,"B");
  var profile=new StudentProfileEntity();profile.setUser(student);profile.setStudentId("ST03-1");profile.setYearLevel(YearLevel.FIRST_YEAR);profiles.saveAndFlush(profile);
  attempt=service.startAttempt(module.getId(),student);
 }
 AssessmentQuestionEntity question(AssessmentEntity assessment,int number,String correct){
  var q=new AssessmentQuestionEntity();q.setAssessment(assessment);q.setQuestionNumber(number);q.setQuestionText("Question");q.setOptionA("A");q.setOptionB("B");q.setOptionC("C");q.setOptionD("D");q.setCorrectAnswer(correct);return questions.saveAndFlush(q);
 }
 StudentAnswerRequest.AnswerItem item(Long id,String answer){var i=new StudentAnswerRequest.AnswerItem();i.setQuestionId(id);i.setAnswer(answer);return i;}
 StudentAnswerRequest request(StudentAnswerRequest.AnswerItem...items){var r=new StudentAnswerRequest();r.setAnswers(Arrays.asList(items));return r;}
 boolean active(){return org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive();}
 void prepareIndex(){
  when(index.getOrBuild(any())).thenAnswer(inv->{
   assertFalse(active(), "Indexing must run outside a database transaction");
   var m=(AiLearningModuleEntity)inv.getArgument(0);m.getFiles().size();
   return new com.ptc.halo.component.ModuleMaterialIndex.Index(m.getId(),"fixture",List.of());
  });
 }
 void respond(Runnable duringAi){
  var response=builder.build().prompt().system(anyString()).user(anyString()).call();
  doAnswer(inv->{
   assertFalse(active(),"AI must run outside a database transaction");duringAi.run();return "{\"inScope\":false,\"chunkIds\":[]}";
  }).when(response).content();
 }
 @Test void normalOpenAndSendUseShortTransactions(){
  prepareIndex();Long id=modules.findAll().get(0).getId();
  var opened=mentor.openSession(id,student);assertNotNull(opened.getSessionId());respond(()->{});
  assertEquals(MentorService.OUT_OF_SCOPE,mentor.sendMessage(opened.getSessionId(),student,"Unrelated question").getHaloMessage());
  assertEquals(3,messages.count());assertEquals(opened.getSessionId(),mentor.openSession(id,student).getSessionId());
 }
 @Test void materialVersionChangeDuringIndexPreventsOpening(){
  Long id=modules.findAll().get(0).getId();
  when(index.getOrBuild(any())).thenAnswer(inv->{assertFalse(active());var m=modules.findById(id).orElseThrow();m.setGenerationToken("changed");modules.saveAndFlush(m);return null;});
  var error=assertThrows(ResponseStatusException.class,()->mentor.openSession(id,student));
  assertEquals("MENTOR_STATE_CHANGED",error.getReason());assertEquals(409,error.getStatusCode().value());assertEquals(0,sessions.count());assertEquals(0,messages.count());
 }
 @Test void revokedAccessDuringAiDoesNotPersistExchange(){
  prepareIndex();var opened=mentor.openSession(modules.findAll().get(0).getId(),student);
  respond(()->{var p=profiles.findByUserId(student.getId()).orElseThrow();p.setYearLevel(YearLevel.SECOND_YEAR);profiles.saveAndFlush(p);});
  var error=assertThrows(ResponseStatusException.class,()->mentor.sendMessage(opened.getSessionId(),student,"Question"));
  assertEquals(403,error.getStatusCode().value());assertEquals(1,messages.count());
 }
 @Test void changedMaterialDuringAiDoesNotPersistExchange(){
  prepareIndex();Long id=modules.findAll().get(0).getId();var opened=mentor.openSession(id,student);
  respond(()->{var m=modules.findById(id).orElseThrow();m.setGenerationToken("new-material");modules.saveAndFlush(m);});
  var error=assertThrows(ResponseStatusException.class,()->mentor.sendMessage(opened.getSessionId(),student,"Question"));
  assertEquals("MENTOR_STATE_CHANGED",error.getReason());assertEquals(1,messages.count());
 }
 @Test void otherStudentCannotSend(){
  prepareIndex();var opened=mentor.openSession(modules.findAll().get(0).getId(),student);clearInvocations(index);
  var other=new UserEntity();other.setId(Long.MAX_VALUE);
  var error=assertThrows(ResponseStatusException.class,()->mentor.sendMessage(opened.getSessionId(),other,"Question"));
  assertEquals("SESSION_NOT_OWNED",error.getReason());verifyNoInteractions(index);assertEquals(1,messages.count());
 }
 @Test void conversationWindowsAreBoundedStableAndIsolated(){
  prepareIndex();Long moduleId=modules.findAll().get(0).getId();var opened=mentor.openSession(moduleId,student);
  var session=sessions.findById(opened.getSessionId()).orElseThrow();messages.deleteAll();
  assertTrue(mentor.getConversation(session.getId(),student).getMessages().isEmpty());
  for(int i=0;i<65;i++)addMessage(session,"Message "+i);
  var page=mentor.openSession(moduleId,student);assertEquals(30,page.getMessages().size());assertTrue(page.isHasOlder());
  var ids=new java.util.ArrayList<Long>();page.getMessages().forEach(m->ids.add(m.getId()));
  addMessage(session,"New arrival");
  var older=mentor.getConversation(session.getId(),student,page.getNextBeforeId());assertEquals(30,older.getMessages().size());assertTrue(older.isHasOlder());
  older.getMessages().forEach(m->assertFalse(ids.contains(m.getId())));older.getMessages().forEach(m->ids.add(m.getId()));
  var oldest=mentor.getConversation(session.getId(),student,older.getNextBeforeId());assertEquals(5,oldest.getMessages().size());assertFalse(oldest.isHasOlder());assertNull(oldest.getNextBeforeId());
  for(var window:List.of(page,older,oldest)){
   var windowIds=window.getMessages().stream().map(com.ptc.halo.dtoResponse.MentorMessageResponse::getId).toList();
   assertEquals(windowIds.stream().sorted().toList(),windowIds);
  }
  var other=new UserEntity();other.setId(Long.MAX_VALUE);
  assertEquals("SESSION_NOT_OWNED",assertThrows(ResponseStatusException.class,()->mentor.getConversation(session.getId(),other,page.getNextBeforeId())).getReason());
 }
 void addMessage(MentorSessionEntity session,String text){var m=new MentorMessageEntity();m.setSession(session);m.setSender(MessageSender.HALO);m.setMessage(text);m.setCreatedAt(LocalDateTime.now());messages.saveAndFlush(m);}
 @Test void concurrentSameRequestCommitsOneExchangeAndReplays() throws Exception {
  prepareIndex();var opened=mentor.openSession(modules.findAll().get(0).getId(),student);
  var barrier=new java.util.concurrent.CyclicBarrier(2);
  respond(()->{try{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}});
  String key=java.util.UUID.randomUUID().toString();var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  try {
   var first=pool.submit(()->mentor.sendMessage(opened.getSessionId(),student,"Question",key));
   var second=pool.submit(()->mentor.sendMessage(opened.getSessionId(),student,"Question",key));
   assertEquals(first.get(20,java.util.concurrent.TimeUnit.SECONDS).getHaloMessage(),second.get(20,java.util.concurrent.TimeUnit.SECONDS).getHaloMessage());
  } finally {pool.shutdownNow();}
  assertEquals(3,messages.count());
  assertEquals(MentorService.OUT_OF_SCOPE,mentor.getExchange(opened.getSessionId(),student,key).getHaloMessage());
  assertEquals(MentorService.OUT_OF_SCOPE,mentor.sendMessage(opened.getSessionId(),student,"Question",key).getHaloMessage());assertEquals(3,messages.count());
  assertEquals("REQUEST_ID_REUSED",assertThrows(ResponseStatusException.class,()->mentor.sendMessage(opened.getSessionId(),student,"Changed",key)).getReason());
  var other=new UserEntity();other.setId(Long.MAX_VALUE);
  assertEquals("SESSION_NOT_OWNED",assertThrows(ResponseStatusException.class,()->mentor.getExchange(opened.getSessionId(),other,key)).getReason());
  assertEquals("EXCHANGE_NOT_FOUND",assertThrows(ResponseStatusException.class,()->mentor.getExchange(opened.getSessionId(),student,java.util.UUID.randomUUID().toString())).getReason());
 }
}