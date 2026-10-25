package com.ptc.halo;
import com.ptc.halo.controller.SelfProfileController;
import com.ptc.halo.service.SelfProfileService;
import com.ptc.halo.service.CustomUserDetailsService;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.security.*;
import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(SelfProfileController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class SelfProfileHttpTest {
 @Autowired MockMvc mvc; @MockBean SelfProfileService profiles; @MockBean UserRepository users;
 @MockBean CustomUserDetailsService details; @MockBean JwtService jwt;
 void token(String role) {
  when(jwt.extractUserId("token")).thenReturn(5L);when(jwt.isCurrent(eq("token"),any())).thenReturn(true);
  when(details.loadUserById(5L)).thenReturn(TestIdentity.details(5L, "actor@test", role));
  var u=new UserEntity();u.setId(5L);u.setRole(Role.valueOf(role));u.setStatus(Status.ACTIVE);when(users.findById(5L)).thenReturn(Optional.of(u));
 }
 @Test void allRolesUseAuthenticatedOwner() throws Exception {
  mvc.perform(patch("/api/profile").contentType("application/json").content("{\"fullName\":\"New\"}")).andExpect(status().isUnauthorized());
  for(String role:List.of("STUDENT","PROFESSOR","ADMIN","SUPER_ADMIN")) {
   token(role);mvc.perform(patch("/api/profile").header("Authorization","Bearer token").contentType("application/json").content("{\"fullName\":\"New\"}")).andExpect(status().isOk());
  }
  verify(profiles,times(4)).updateName(argThat(a->a.getName().equals("actor@test")),eq("New"));
 }
 @Test void targetingAndActingUpdatesAreRejected() throws Exception {
  token("ADMIN");
  for(String payload:List.of("{\"fullName\":\"New\",\"userId\":99}","{\"fullName\":\"New\",\"email\":\"other@test\"}","{\"fullName\":\"New\",\"role\":\"SUPER_ADMIN\"}","{\"fullName\":\"New\",\"status\":\"ACTIVE\"}"))
   mvc.perform(patch("/api/profile").header("Authorization","Bearer token").contentType("application/json").content(payload)).andExpect(status().isBadRequest());
  mvc.perform(patch("/api/profile").header("Authorization","Bearer token").header("X-Acting-Session",UUID.randomUUID()).contentType("application/json").content("{\"fullName\":\"Target\"}")).andExpect(status().isForbidden());
  verifyNoInteractions(profiles);
 }

 @Test void validationErrorsHaveSafeSpecificMessages() throws Exception {
  token("STUDENT");
  for(String code:List.of("FULL_NAME_REQUIRED","FULL_NAME_TOO_LONG","INVALID_FULL_NAME")) {
   doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,code)).when(profiles).updateName(any(),anyString());
   mvc.perform(patch("/api/profile").header("Authorization","Bearer token").contentType("application/json").content("{\"fullName\":\"test\"}"))
    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(code)));
  }
 }

 @Test void serverFailuresRemainServerErrorsWithoutInternalDetails() throws Exception {
  token("STUDENT");
  doThrow(new IllegalStateException("private SQL details")).when(profiles).updateName(any(),anyString());
  mvc.perform(patch("/api/profile").header("Authorization","Bearer token").contentType("application/json").content("{\"fullName\":\"Talidong, Archie V.\"}"))
    .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL details"))));
 }

 @Test void browserPreflightAllowsAuthenticatedPatch() throws Exception {
  mvc.perform(options("/api/profile").header("Origin","http://localhost:5173")
    .header("Access-Control-Request-Method","PATCH").header("Access-Control-Request-Headers","authorization,content-type"))
    .andExpect(status().isOk())
    .andExpect(header().string("Access-Control-Allow-Methods",org.hamcrest.Matchers.containsString("PATCH")))
    .andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
 }
}
