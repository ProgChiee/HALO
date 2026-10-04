package com.ptc.halo;

import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.ProfessorDashboardController;
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

@WebMvcTest(ProfessorDashboardController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class ProfessorDashboardScopeHttpTest {
 @Autowired MockMvc mvc;
 @MockBean ProfessorDashboardService service;
 @MockBean UserRepository users;
 @MockBean CustomUserDetailsService details;
 @MockBean JwtService jwt;
 UserEntity token(String role) {
  when(jwt.extractUsername("test-token")).thenReturn("prof@example.test");when(jwt.isCurrent(eq("test-token"),any())).thenReturn(true);
  when(details.loadUserByUsername("prof@example.test")).thenReturn(User.withUsername("prof@example.test").password("unused").roles(role).build());
  var actor=new UserEntity();actor.setId(7L);actor.setRole(Role.PROFESSOR);actor.setEmail("prof@example.test");when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));return actor;
 }

 @Test void dashboardUsesAuthenticatedProfessorNotRequestOwner() throws Exception {
  var actor=token("PROFESSOR");
  var response = new com.ptc.halo.dtoResponse.ProfessorDashboardResponse(); response.setTotalModules(0L);
  when(service.getDashboard(actor)).thenReturn(response);
  mvc.perform(get("/api/professor/dashboard").param("professorId","999").header("Authorization","Bearer test-token"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.totalModules").value(0));
  verify(service).getDashboard(actor);
 }
 @Test void roleRestrictionsRemain() throws Exception {
  mvc.perform(get("/api/professor/dashboard")).andExpect(status().isUnauthorized());
  for(String role:List.of("ADMIN","STUDENT","SUPER_ADMIN")){token(role);mvc.perform(get("/api/professor/dashboard").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());}
  verifyNoInteractions(service);
 }
}
