package com.ptc.halo;

import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.ProfessorStudentController;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.Role;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfessorStudentController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class ProfessorStudentScopeHttpTest {
 @Autowired MockMvc mvc;
 @MockBean ProfessorStudentService service;
 @MockBean UserRepository users;
 @MockBean CustomUserDetailsService details;
 @MockBean JwtService jwt;
 UserEntity token(String role) {
  when(jwt.extractUsername("test-token")).thenReturn("prof@example.test");when(jwt.isCurrent(eq("test-token"),any())).thenReturn(true);
  when(details.loadUserByUsername("prof@example.test")).thenReturn(User.withUsername("prof@example.test").password("unused").roles(role).build());
  var actor=new UserEntity();actor.setId(7L);actor.setRole(Role.PROFESSOR);actor.setEmail("prof@example.test");when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));return actor;
 }
 @Test void allReadsReceiveAuthenticatedProfessorIgnoringQueryOwner() throws Exception {
  var actor=token("PROFESSOR");
  for(String path:List.of("","/10/progress","/10/subjects","/10/assessments","/10/badges"))
   mvc.perform(get("/api/professor/students"+path).param("professorId","999").header("Authorization","Bearer test-token")).andExpect(status().isOk());
  verify(service).getStudents(actor);verify(service).getStudentProgress(10L,actor);verify(service).getStudentSubjects(10L,actor);verify(service).getStudentAssessmentHistory(10L,actor);verify(service).getStudentBadges(10L,actor);
 }
 @Test void invalidStudentReturnsSafe404() throws Exception {
  var actor=token("PROFESSOR");var error=new ResponseStatusException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND");
  when(service.getStudentProgress(99L,actor)).thenThrow(error);when(service.getStudentSubjects(99L,actor)).thenThrow(error);when(service.getStudentAssessmentHistory(99L,actor)).thenThrow(error);when(service.getStudentBadges(99L,actor)).thenThrow(error);
  for(String suffix:List.of("progress","subjects","assessments","badges")) mvc.perform(get("/api/professor/students/99/"+suffix).header("Authorization","Bearer test-token"))
   .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND")).andExpect(jsonPath("$.status").value(404));
 }
 @Test void summaryUsesAuthenticatedOwnerAndReturnsStablePageShape() throws Exception {
  var actor=token("PROFESSOR");
  var row=new com.ptc.halo.dtoResponse.ProfessorStudentSummary(10L,"Student",null,1,2,3);
  when(service.getProgressSummaries(actor,0,20)).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(row),org.springframework.data.domain.PageRequest.of(0,20),1));
  mvc.perform(get("/api/professor/students/progress-summaries").param("professorId","999").header("Authorization","Bearer test-token"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].completedModules").value(1))
   .andExpect(jsonPath("$.content[0].email").doesNotExist()).andExpect(jsonPath("$.totalElements").value(1))
   .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.badgeScope").value("INSTITUTION_WIDE"));
  verify(service).getProgressSummaries(actor,0,20);
  when(service.getProgressSummaries(actor,-1,20)).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_PAGINATION"));
  mvc.perform(get("/api/professor/students/progress-summaries").param("page","-1").header("Authorization","Bearer test-token"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
 }
 @Test void roleRestrictionsRemain() throws Exception {
  mvc.perform(get("/api/professor/students")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/professor/students/progress-summaries")).andExpect(status().isUnauthorized());
  for(String role:List.of("ADMIN","STUDENT","SUPER_ADMIN")){token(role);for(String suffix:List.of("","/progress-summaries"))mvc.perform(get("/api/professor/students"+suffix).header("Authorization","Bearer test-token")).andExpect(status().isForbidden());}
  verifyNoInteractions(service);
 }
}
