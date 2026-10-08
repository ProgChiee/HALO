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
@Import({com.ptc.halo.service.ProfessorModuleService.class,SecurityConfig.class, JwtAuthenticationFilter.class})
class ProfessorModuleHttpTest {
 @Autowired MockMvc mvc;
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
 @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
 @Test void directUploadsReturnStructuredValidationErrors() throws Exception {
  token("PROFESSOR");var module=new com.ptc.halo.entity.AiLearningModuleEntity();module.setStatus(com.ptc.halo.enums.LessonStatus.PENDING);
  when(modules.findByIdAndWeek_Subject_Professor_User_Id(2L,7L)).thenReturn(Optional.of(module));
  var real=new FileUploadService(new LessonStoragePaths(directory.toString()));
  when(storage.uploadFile(any(),eq(module))).thenAnswer(call->real.uploadFile(call.getArgument(0),module));
  mvc.perform(multipart(base+"/2/files").file(new org.springframework.mock.web.MockMultipartFile("file","fake.pdf","application/pdf","not PDF".getBytes())).header("Authorization","Bearer test-token"))
   .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(415)).andExpect(jsonPath("$.code").value("UPLOAD_FILE_UNSUPPORTED"));
  for(int i=0;i<10;i++)module.addFile(new com.ptc.halo.entity.AiLearningFileEntity());
  mvc.perform(multipart(base+"/2/files").file(new org.springframework.mock.web.MockMultipartFile("file","fake.pdf","application/pdf",new byte[]{1})).header("Authorization","Bearer test-token"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MODULE_FILE_LIMIT_EXCEEDED"));
  verifyNoInteractions(audit,files);
 }
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

 @Test void staleGenerationAndOptimisticWritesReturnSafe409() throws Exception {
  token("PROFESSOR");var module=new com.ptc.halo.entity.AiLearningModuleEntity();module.setStatus(com.ptc.halo.enums.LessonStatus.PENDING);
  when(modules.findByIdAndWeek_Subject_Professor_User_Id(2L,7L)).thenReturn(Optional.of(module));
  when(generation.generateLesson(2L)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"STALE_MODULE_OPERATION"));
  mvc.perform(post(base+"/2/generate").header("Authorization","Bearer test-token"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_MODULE_OPERATION"));
  doThrow(new org.springframework.dao.OptimisticLockingFailureException("internal detail")).when(generation).generateLesson(2L);
  mvc.perform(post(base+"/2/generate").header("Authorization","Bearer test-token"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_MODULE_OPERATION"))
   .andExpect(jsonPath("$.message").value("This module changed while the operation was running. Reload it before trying again."));
  verifyNoInteractions(audit);
 }

 @Test void filelessPublicationReturnsActionableConflict() throws Exception {
  token("PROFESSOR");var module=new com.ptc.halo.entity.AiLearningModuleEntity();module.setStatus(com.ptc.halo.enums.LessonStatus.PENDING);module.setAiGenerationStatus(com.ptc.halo.enums.AiGenerationStatus.COMPLETED);
  when(modules.findByIdAndWeek_Subject_Professor_User_Id(2L,7L)).thenReturn(Optional.of(module));
  mvc.perform(put(base+"/2/approve").header("Authorization","Bearer test-token"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MODULE_MATERIALS_REQUIRED"))
   .andExpect(jsonPath("$.message").value("Upload at least one original lesson file before publishing."));
  verifyNoInteractions(materialIndex,assessment,audit);
 }
}
