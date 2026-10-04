package com.ptc.halo;

import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.ProfessorAcademicController;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.dtoResponse.AdminRecentActivityResponse;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfessorAcademicController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ProfessorAcademicHttpTest {
 @Autowired MockMvc mvc;
 @MockBean ProfessorAcademicService service;
 @MockBean UserRepository users;
 @MockBean CustomUserDetailsService details;
 @MockBean JwtService jwt;
 void token(String role) {
  when(jwt.extractUsername("test-token")).thenReturn("prof@example.test");
  when(jwt.isCurrent(eq("test-token"),any())).thenReturn(true);
  when(details.loadUserByUsername("prof@example.test")).thenReturn(User.withUsername("prof@example.test").password("unused").roles(role).build());
 }
 @Test void authenticatedIdentityIsPassedToEveryAcademicOperation() throws Exception {
  token("PROFESSOR");var actor=new UserEntity();actor.setId(7L);actor.setEmail("prof@example.test");
  when(users.findByEmail(actor.getEmail())).thenReturn(java.util.Optional.of(actor));
  when(service.viewAllSubjects(actor)).thenReturn(List.of());
  mvc.perform(get("/api/professor/subjects").header("Authorization","Bearer test-token")).andExpect(status().isOk());
  mvc.perform(get("/api/professor/subjects/1").header("Authorization","Bearer test-token")).andExpect(status().isOk());
  mvc.perform(get("/api/professor/subjects/1/weeks").header("Authorization","Bearer test-token")).andExpect(status().isOk());
  mvc.perform(get("/api/professor/weeks/2").header("Authorization","Bearer test-token")).andExpect(status().isOk());
  verify(service).viewAllSubjects(actor);verify(service).viewSubjectById(1L,actor);verify(service).viewAllWeeks(1L,actor);verify(service).viewWeekById(2L,actor);
 }
 @Test void scopedFailuresAreSafe404ForDirectRequests() throws Exception {
  token("PROFESSOR");var actor=new UserEntity();actor.setId(7L);
  when(users.findByEmail("prof@example.test")).thenReturn(java.util.Optional.of(actor));
  var denied=new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND");
  when(service.viewSubjectById(1L,actor)).thenThrow(denied);
  when(service.viewWeekById(2L,actor)).thenThrow(denied);
  for(String path:List.of("/api/professor/subjects/1","/api/professor/weeks/2")) mvc.perform(get(path).header("Authorization","Bearer test-token"))
   .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
   .andExpect(jsonPath("$.message").value("The requested resource is unavailable."));
 }
 @Test void roleRestrictionsRemain() throws Exception {
  mvc.perform(get("/api/professor/subjects")).andExpect(status().isUnauthorized());
  for(String role:List.of("ADMIN","SUPER_ADMIN","STUDENT")) {
   token(role);mvc.perform(get("/api/professor/subjects").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
  }
  verifyNoInteractions(service);
 }
}
