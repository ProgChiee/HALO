package com.ptc.halo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptc.halo.component.ModuleMaterialIndex;
import com.ptc.halo.entity.*;
import com.ptc.halo.repository.AiLearningModuleRepository;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LessonMaterialRelevanceTest {
 @TempDir Path directory;
 ChatClient client=mock(ChatClient.class,RETURNS_DEEP_STUBS);
 ModuleMaterialIndex index=mock(ModuleMaterialIndex.class);
 ModuleGenerationState state=mock(ModuleGenerationState.class);
 AiLearningModuleRepository repository=mock(AiLearningModuleRepository.class);
 AiLearningModuleEntity module=new AiLearningModuleEntity();
 AiGenerationService service;
 @BeforeEach void setup() throws Exception {
  var builder=mock(ChatClient.Builder.class);when(builder.build()).thenReturn(client);
  var storage=mock(LessonStoragePaths.class);var path=directory.resolve("opaque.pdf");Files.writeString(path,"test media");when(storage.resolve(anyString())).thenReturn(path);
  var subject=new SubjectEntity();subject.setSubjectName("Front Office Operations");subject.setDescription("Hotel guest service");
  var week=new WeekEntity();week.setSubject(subject);week.setWeekNumber(3);week.setTitle("Guest arrival and check-in");module.setWeek(week);
  module.setGeneratedSummary("Saved lesson");module.setLessonText("Guest check-in");
  var file=new AiLearningFileEntity();file.setFilePath("opaque.pdf");file.setFileType("application/pdf");file.setOriginalFileName("misleading-name.pdf");module.addFile(file);
  when(state.begin(1L)).thenReturn(module);when(state.finish(eq(1L),any())).thenAnswer(i->i.getArgument(1));
  service=new AiGenerationService(builder,repository,new ObjectMapper(),index,state,storage);
 }
 @ParameterizedTest
 @CsvSource({"Hotel check-in and guest registration,true", "Quantum particle physics,false", "Pastry preparation and baking temperatures,false", "Welcoming arrivals and allocating accommodation,true"})
 void extractedContentIsSemanticallyGatedBeforeGeneration(String extracted,boolean relevant) {
  when(index.getOrBuild(module)).thenReturn(new ModuleMaterialIndex.Index(1L,"fingerprint",List.of(new ModuleMaterialIndex.Chunk("chunk",null,"filename",1,extracted,""))));
  when(client.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("{\"relevant\":"+relevant+"}");
  when(client.prompt().user(any(java.util.function.Consumer.class)).call().content()).thenReturn("{\"valid\":true,\"objectives\":\"Learn\",\"knowledge\":\"Content\",\"examples\":\"Example\",\"summary\":\"New lesson\"}");
  if(relevant) assertEquals("New lesson",service.generateLesson(1L).getGeneratedSummary());
  else {
   var error=assertThrows(ResponseStatusException.class,()->service.generateLesson(1L));assertEquals(422,error.getStatusCode().value());assertEquals("MATERIAL_NOT_RELEVANT",error.getReason());
   assertEquals("Saved lesson",module.getGeneratedSummary());verify(state,never()).finish(anyLong(),any());
   verify(client.prompt(),never()).user(any(java.util.function.Consumer.class));
   var response=com.ptc.halo.controller.ProfessorErrorResponses.status(error);assertEquals(422,response.getStatusCode().value());assertEquals("MATERIAL_NOT_RELEVANT",response.getBody().get("code"));
  }
  verify(client.prompt().system(anyString())).user(argThat((String prompt)->prompt.contains(extracted)&&prompt.contains("Front Office Operations")&&prompt.contains("Guest arrival and check-in")&&!prompt.contains("misleading-name.pdf")));
 }
 @Test void unreadableMaterialDoesNotCallProviderOrSave() {
  when(index.getOrBuild(module)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,"MODULE_MATERIAL_UNREADABLE"));
  assertEquals("MODULE_MATERIAL_UNREADABLE",assertThrows(ResponseStatusException.class,()->service.generateLesson(1L)).getReason());
  verifyNoInteractions(client);verify(state,never()).finish(anyLong(),any());assertEquals("Saved lesson",module.getGeneratedSummary());
 }
 @Test void malformedRelevanceVerdictFailsClosedWithoutChangingLesson() {
  when(index.getOrBuild(module)).thenReturn(new ModuleMaterialIndex.Index(1L,"f",List.of(new ModuleMaterialIndex.Chunk("c",null,"file",1,"Content",""))));
  when(client.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("{}");
  assertEquals("MATERIAL_RELEVANCE_UNAVAILABLE",assertThrows(ResponseStatusException.class,()->service.generateLesson(1L)).getReason());
  verify(state,never()).finish(anyLong(),any());assertEquals("Saved lesson",module.getGeneratedSummary());
 }
}
