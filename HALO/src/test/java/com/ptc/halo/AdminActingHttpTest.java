package com.ptc.halo;
import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.AdminActingController;
import com.ptc.halo.dtoRequest.AdminActingSessionRequest;
import com.ptc.halo.dtoResponse.*;
import com.ptc.halo.enums.Role;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminActingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminActingHttpTest {
 @Autowired MockMvc mvc;
 @MockBean AdminActingSessionService service;
 @MockBean CustomUserDetailsService details;
 @MockBean JwtService jwt;
 static final String PATH="/api/admin/acting";
 static final String ID="acdf8250-f785-4e34-b508-a9b1546c1470";
 void token(String role) {
  when(jwt.extractUsername("test-token")).thenReturn("admin@example.test");
  when(jwt.isCurrent(eq("test-token"),any())).thenReturn(true);
  when(details.loadUserByUsername("admin@example.test")).thenReturn(User.withUsername("admin@example.test").password("unused").roles(role).build());
 }
 @Test void adminEndpointsKeepPrincipalAndUseStoredTarget() throws Exception {
  token("ADMIN");
  var target=new AdminActingTargetResponse(2L,"Professor","prof@example.test",Role.PROFESSOR);
  var response=new AdminActingSessionResponse(ID,1L,target,Instant.now(),Instant.now().plusSeconds(1800));
  when(service.create(any(),any())).thenAnswer(call->{
   org.junit.jupiter.api.Assertions.assertEquals("admin@example.test",((org.springframework.security.core.Authentication)call.getArgument(0)).getName());
   return response;
  });
  when(service.validate(any(),eq(ID))).thenReturn(response);
  when(service.targets(any(),eq(Role.PROFESSOR),eq(0),eq(20),eq(""))).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(target)));
  mvc.perform(post(PATH+"/sessions").header("Authorization","Bearer test-token").contentType("application/json")
   .content("{\"targetUserId\":2,\"targetRole\":\"PROFESSOR\"}"))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.adminUserId").value(1)).andExpect(jsonPath("$.target.userId").value(2));
  mvc.perform(get(PATH+"/sessions/"+ID).param("targetUserId","999").header("Authorization","Bearer test-token"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.target.userId").value(2));
  verify(service).validate(any(),eq(ID));
  mvc.perform(get(PATH+"/targets").param("role","PROFESSOR").header("Authorization","Bearer test-token"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].role").value("PROFESSOR"));
  mvc.perform(delete(PATH+"/sessions/"+ID).header("Authorization","Bearer test-token")).andExpect(status().isNoContent());
 }
 @Test void anonymousAndOtherRolesCannotUseAnyEndpoint() throws Exception {
  mvc.perform(get(PATH+"/targets").param("role","STUDENT")).andExpect(status().isUnauthorized());
  for(String role:List.of("STUDENT","PROFESSOR","SUPER_ADMIN")) {
   token(role);
   mvc.perform(get(PATH+"/targets").param("role","STUDENT").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
   mvc.perform(post(PATH+"/sessions").header("Authorization","Bearer test-token").contentType("application/json").content("{}" )).andExpect(status().isForbidden());
   mvc.perform(get(PATH+"/sessions/"+ID).header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
   mvc.perform(delete(PATH+"/sessions/"+ID).header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
  }
  verifyNoInteractions(service);
 }
 @Test void adminStillCannotEnterNormalProfessorOrStudentEndpoints() throws Exception {
  token("ADMIN");
  for(String path:List.of("/api/professor/subjects","/api/student/assessment/1"))
   mvc.perform(get(path).header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
 }
 @Test void malformedAndInvalidRequestsHaveSafe400() throws Exception {
  token("ADMIN");
  for(String body:List.of("{", "{}", "{\"targetUserId\":0,\"targetRole\":\"STUDENT\"}", "{\"targetUserId\":2,\"targetRole\":\"UNKNOWN\"}"))
   mvc.perform(post(PATH+"/sessions").header("Authorization","Bearer test-token").contentType("application/json").content(body))
    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  mvc.perform(get(PATH+"/sessions/not-a-uuid").header("Authorization","Bearer test-token")).andExpect(status().isBadRequest());
  verifyNoInteractions(service);
 }
 @Test void businessAndUnexpectedErrorsAreStructured() throws Exception {
  token("ADMIN");
  for(var code:List.of(AdminApiException.Code.RESOURCE_NOT_FOUND,AdminApiException.Code.INVALID_TARGET_ROLE,
     AdminApiException.Code.ACTING_TARGET_INACTIVE,AdminApiException.Code.ACTING_SESSION_INVALID)) {
   when(service.validate(any(),eq(ID))).thenThrow(new AdminApiException(code));
   mvc.perform(get(PATH+"/sessions/"+ID).header("Authorization","Bearer test-token"))
    .andExpect(status().is(code.status)).andExpect(jsonPath("$.code").value(code.name()));
  }
  when(service.validate(any(),eq(ID))).thenThrow(new IllegalStateException("secret internal SQL"));
  mvc.perform(get(PATH+"/sessions/"+ID).header("Authorization","Bearer test-token"))
   .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("ADMIN_REQUEST_FAILED"))
   .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
 }
}
