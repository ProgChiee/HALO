package com.ptc.halo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptc.halo.component.ModuleMaterialIndex;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;

@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.open-in-view=false"},showSql=false)
@Import({AiGenerationService.class,ModuleGenerationState.class,ModuleGenerationConcurrencyTest.Config.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ModuleGenerationConcurrencyTest {
 static final ChatClient CLIENT=mock(ChatClient.class,RETURNS_DEEP_STUBS);
 @TestConfiguration static class Config {
  @Bean ChatClient.Builder builder(){var builder=mock(ChatClient.Builder.class);when(builder.build()).thenReturn(CLIENT);return builder;}
  @Bean ObjectMapper mapper(){return new ObjectMapper();}
 }
 @MockBean ModuleMaterialIndex index;
 @MockBean LessonStoragePaths storage;
 @Autowired AiGenerationService service;
 @Autowired ModuleGenerationState state;
 @Autowired AiLearningModuleRepository modules;
 @Autowired WeekRepository weeks;
 @Autowired SubjectRepository subjects;
 Long id;
 @BeforeEach void setup(){
  reset(CLIENT);modules.deleteAll();weeks.deleteAll();subjects.deleteAll();
  var s=new SubjectEntity();s.setSubjectCode("CONCURRENCY");s.setSubjectName("Subject");s=subjects.saveAndFlush(s);
  var w=new WeekEntity();w.setSubject(s);w.setWeekNumber(1);w.setTitle("Week");w=weeks.saveAndFlush(w);
  var m=new AiLearningModuleEntity();m.setWeek(w);m.setLessonText("Original materials");m.setStatus(LessonStatus.PENDING);m.setAiGenerationStatus(AiGenerationStatus.PENDING);id=modules.saveAndFlush(m).getId();
 }
 String valid(String summary){return "{\"valid\":true,\"objectives\":\"Learn\",\"knowledge\":\"Concept\",\"examples\":\"Example\",\"summary\":\""+summary+"\"}";}
 void stale(){var e=assertThrows(ResponseStatusException.class,()->service.generateLesson(id));assertEquals(409,e.getStatusCode().value());assertEquals("STALE_MODULE_OPERATION",e.getReason());}
 void changeMaterials(){var latest=modules.findWithFilesById(id).orElseThrow();latest.setLessonText("New materials");latest.setGenerationToken(null);modules.saveAndFlush(latest);}
 @Test void materialEditRejectsOldSuccessWithoutOverwritingMaterials(){
  when(CLIENT.prompt().user(anyString()).call().content()).thenAnswer(i->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());changeMaterials();return valid("Stale");});
  stale();var current=modules.findById(id).orElseThrow();assertEquals("New materials",current.getLessonText());assertNull(current.getGeneratedSummary());assertEquals(AiGenerationStatus.PENDING,current.getAiGenerationStatus());
 }
 @Test void staleTechnicalFailureCannotMarkNewerStateFailed(){
  when(CLIENT.prompt().user(anyString()).call().content()).thenAnswer(i->{changeMaterials();throw new IllegalStateException("API failed");});
  stale();assertEquals(AiGenerationStatus.PENDING,modules.findById(id).orElseThrow().getAiGenerationStatus());
 }
 @Test void newerGenerationWinsAndPublicationCannotBeOverwritten(){
  var calls=new AtomicInteger();
  when(CLIENT.prompt().user(anyString()).call().content()).thenAnswer(i->{
   assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
   if(calls.getAndIncrement()==0){service.generateLesson(id);service.approveLesson(id);return valid("Old result");}
   return valid("New result");
  });
  stale();var current=modules.findById(id).orElseThrow();assertEquals("New result",current.getGeneratedSummary());assertEquals(LessonStatus.APPROVED,current.getStatus());assertEquals(AiGenerationStatus.COMPLETED,current.getAiGenerationStatus());
 }
 @Test void newerGenerationWinsEvenWhenBothStartPending(){
  var calls=new AtomicInteger();
  when(CLIENT.prompt().user(anyString()).call().content()).thenAnswer(i->{if(calls.getAndIncrement()==0){service.generateLesson(id);return valid("Old");}return valid("New");});
  stale();assertEquals("New",modules.findById(id).orElseThrow().getGeneratedSummary());
 }
 @Test void normalGenerationCommitsAndVersionProtectsOtherWriters(){
  when(CLIENT.prompt().user(anyString()).call().content()).thenAnswer(i->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());return valid("Normal");});
  var initial=modules.findById(id).orElseThrow().getVersion();
  assertEquals("Normal",service.generateLesson(id).getGeneratedSummary());assertTrue(modules.findById(id).orElseThrow().getVersion()>initial);
  var old=modules.findById(id).orElseThrow();var fresh=modules.findById(id).orElseThrow();fresh.setLessonText("Fresh");modules.saveAndFlush(fresh);old.setLessonText("Stale");
  assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,()->modules.saveAndFlush(old));assertEquals("Fresh",modules.findById(id).orElseThrow().getLessonText());
 }
}
