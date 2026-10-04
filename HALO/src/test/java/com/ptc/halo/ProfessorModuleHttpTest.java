package com.ptc.halo;

import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.AiLearningModuleController;
import com.ptc.halo.entity.UserEntity;
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

@WebMvcTest(AiLearningModuleController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ProfessorModuleHttpTest {
 @Autowired MockMvc mvc;
 @MockBean AiLearningModuleRepository modules;
 @MockBean AiLearningFileRepository files;
 @MockBean WeekRepository weeks;
 @MockBean UserRepository users;
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
 @Test void inaccessibleResourcesReturnStructured404ForEveryAction() throws Exception {
  token("PROFESSOR");
  var requests=List.of(get(base+"/week/1"), get(base+"/2"),
    put(base+"/2").contentType("application/json").content("{\"lessonText\":\"changed\"}"),
    post(base+"/2/generate"),put(base+"/2/approve"),put(base+"/2/decline"),delete(base+"/files/3"),
    multipart(base).file("files",new byte[]{1}).param("weekId","1"),
    multipart(base+"/2/files").file("file",new byte[]{1}));
  for(var request:requests) mvc.perform(request.header("Authorization","Bearer test-token"))
    .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404))
    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
    .andExpect(jsonPath("$.message").value("The requested resource is unavailable."));
  verifyNoInteractions(storage,generation,assessment,audit);
 }
 @Test void authenticationAndProfessorRoleAreStillRequired() throws Exception {
  mvc.perform(get(base+"/2")).andExpect(status().isUnauthorized());
  for(String role:List.of("ADMIN","SUPER_ADMIN","STUDENT")) {
   token(role);mvc.perform(get(base+"/2").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
  }
  verifyNoInteractions(modules,files,weeks,storage,generation,assessment,audit);
 }
}
