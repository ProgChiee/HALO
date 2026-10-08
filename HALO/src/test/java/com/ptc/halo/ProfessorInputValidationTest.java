package com.ptc.halo;

import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.AiLearningModuleController;
import com.ptc.halo.entity.*;
import com.ptc.halo.controller.ProfessorAcademicController;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AiLearningModuleController.class, ProfessorAcademicController.class})
@Import({com.ptc.halo.service.ProfessorModuleService.class,SecurityConfig.class, JwtAuthenticationFilter.class})
class ProfessorInputValidationTest {
 @Autowired MockMvc mvc;
 @MockBean ProfessorAcademicService academic;
 @MockBean AiLearningModuleRepository modules;
 @MockBean AiLearningFileRepository files;
 @MockBean WeekRepository weeks;
 @MockBean UserRepository users;
 @MockBean com.ptc.halo.component.ModuleMaterialIndex materialIndex;
 @MockBean FileUploadService storage;
 @MockBean AiGenerationService generation;
 @MockBean AssessmentService assessment;
 @MockBean ActivityLogService audit;
 @MockBean CustomUserDetailsService details;
 @MockBean JwtService jwt;
 final String base="/api/professor/ai-learning-modules";
 void token(String role) {
  when(jwt.extractUsername("test-token")).thenReturn("prof@example.test");
  when(jwt.isCurrent(eq("test-token"),any())).thenReturn(true);
  when(details.loadUserByUsername("prof@example.test")).thenReturn(User.withUsername("prof@example.test").password("unused").roles(role).build());
  var actor=new UserEntity();actor.setId(7L);actor.setEmail("prof@example.test");when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
 }

 void bad(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
  mvc.perform(request.header("Authorization","Bearer test-token")).andExpect(status().isBadRequest())
   .andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.errors").isMap());
 }
 @Test void subjectAndWeekInputsAreValidatedBeforeService() throws Exception {
  token("PROFESSOR");
  for(String body:List.of("{}", "{\"subjectCode\":\"   \",\"subjectName\":\"X\",\"yearLevel\":\"FIRST_YEAR\"}","{\"subjectCode\":\"S\",\"subjectName\":\" \",\"yearLevel\":\"FIRST_YEAR\"}")) {
   bad(post("/api/professor/subjects").contentType("application/json").content(body));
   bad(put("/api/professor/subjects/1").contentType("application/json").content(body));
  }
  for(String body:List.of("{}","{\"weekNumber\":0,\"title\":\"Week\"}","{\"weekNumber\":-1,\"title\":\"Week\"}","{\"weekNumber\":1,\"title\":\"   \"}","{\"weekNumber\":\"bad\",\"title\":\"Week\"}")) {
   bad(post("/api/professor/subjects/1/weeks").contentType("application/json").content(body));
   bad(put("/api/professor/weeks/1").contentType("application/json").content(body));
  }
  bad(put("/api/professor/weeks/1").contentType("application/json").content("{\"weekNumber\":1.5,\"title\":\"Week\"}"));
  for(String id:List.of("0","-1","bad")) {
   bad(delete("/api/professor/subjects/"+id));bad(delete("/api/professor/weeks/"+id));bad(put(base+"/"+id).contentType("application/json").content("{}"));
  }
  verifyNoInteractions(academic,storage,generation,audit);
 }
 @Test void normalizedValidAcademicRequestsReachService() throws Exception {
  token("PROFESSOR");
  String subject="{\"subjectCode\":\" S \",\"subjectName\":\" Name \",\"description\":\" Description \",\"yearLevel\":\"FIRST_YEAR\"}";
  mvc.perform(post("/api/professor/subjects").contentType("application/json").content(subject).header("Authorization","Bearer test-token")).andExpect(status().isOk());
  mvc.perform(put("/api/professor/subjects/1").contentType("application/json").content(subject).header("Authorization","Bearer test-token")).andExpect(status().isOk());
  verify(academic).createSubject(argThat(r->r.getSubjectCode().equals("S")&&r.getSubjectName().equals("Name")&&r.getDescription().equals("Description")),any());
  verify(academic).updateSubject(eq(1L),argThat(r->r.getSubjectCode().equals("S")),any());
  for(var request:List.of(post("/api/professor/subjects/1/weeks"),put("/api/professor/weeks/1"))) mvc.perform(request.contentType("application/json").content("{\"weekNumber\":1,\"title\":\" Week \"}").header("Authorization","Bearer test-token")).andExpect(status().isOk());
  verify(academic).createWeek(eq(1L),argThat(r->r.getTitle().equals("Week")),any());verify(academic).updateWeek(eq(1L),argThat(r->r.getTitle().equals("Week")),any());
 }
 @Test void moduleCannotLoseAllSourcesAndValidUpdatesRemainAllowed() throws Exception {
  token("PROFESSOR");var module=new AiLearningModuleEntity();module.setWeek(new WeekEntity());module.setStatus(LessonStatus.PENDING);
  module.setLessonText("Original");module.setGeneratedKnowledge("Keep on rejection");
  when(modules.findByIdAndWeek_Subject_Professor_User_Id(2L,7L)).thenReturn(Optional.of(module));when(modules.save(any())).thenAnswer(i->i.getArgument(0));
  bad(put(base+"/2").contentType("application/json").content("{\"lessonText\":\"   \",\"aiNotes\":\"notes only\"}"));
  org.junit.jupiter.api.Assertions.assertEquals("Original",module.getLessonText());
  verify(modules,never()).save(any());verifyNoInteractions(audit);
  bad(put(base+"/2").contentType("application/json").content("{\"youtubeLink\":\"javascript:alert(1)\"}"));
  bad(put(base+"/2").contentType("application/json").content("{\"youtubeLink\":\""+"x".repeat(256)+"\"}"));
  mvc.perform(put(base+"/2").contentType("application/json").content("{\"lessonText\":\" lesson \",\"aiNotes\":\" notes \"}").header("Authorization","Bearer test-token")).andExpect(status().isOk()).andExpect(jsonPath("$.lessonText").value("lesson")).andExpect(jsonPath("$.aiNotes").value("notes"));
  module.addFile(new AiLearningFileEntity());
  mvc.perform(put(base+"/2").contentType("application/json").content("{}").header("Authorization","Bearer test-token")).andExpect(status().isOk());
 }
}
