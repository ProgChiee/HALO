package com.ptc.halo;
import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.SuperAdminController;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import com.ptc.halo.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SuperAdminController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SuperAdminActivityHttpTest {
 @Autowired MockMvc mvc;
 @MockBean SuperAdminService admins;
 @MockBean ActivityLogService logs;
 @MockBean UserRepository users;
 @MockBean ProfileService profiles;
 @MockBean CustomUserDetailsService details;
 @MockBean JwtService jwt;
 void token(String role) {
  when(jwt.extractUsername("test-token")).thenReturn("test@example.test");
  when(jwt.isCurrent(eq("test-token"), any())).thenReturn(true);
  when(details.loadUserByUsername("test@example.test")).thenReturn(User.withUsername("test@example.test").password("unused").roles(role).build());
 }
 @Test void superAdminCanReadLogs() throws Exception {
  token("SUPER_ADMIN"); when(logs.getSuperAdminLogs(0, 20, "", null)).thenReturn(org.springframework.data.domain.Page.empty());
  mvc.perform(get("/api/super-admin/activity-logs").header("Authorization","Bearer test-token")).andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
 }
 @Test void unexpectedFailureIsSafe500() throws Exception {
  token("SUPER_ADMIN"); when(logs.getSuperAdminLogs(0, 20, "", null)).thenThrow(new IllegalStateException("private database details"));
  mvc.perform(get("/api/super-admin/activity-logs").header("Authorization","Bearer test-token"))
   .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("ACTIVITY_LOGS_UNAVAILABLE"))
   .andExpect(jsonPath("$.message").value("Activity logs are temporarily unavailable."));
 }
 @Test void unauthenticatedStill401() throws Exception {
  mvc.perform(get("/api/super-admin/activity-logs")).andExpect(status().isUnauthorized()); verifyNoInteractions(logs);
 }
 @Test void adminStillForbidden() throws Exception {
  token("ADMIN"); mvc.perform(get("/api/super-admin/activity-logs").header("Authorization","Bearer test-token")).andExpect(status().isForbidden()); verifyNoInteractions(logs);
 }

 @Test void invalidCreateFieldsReturn400() throws Exception {
  token("SUPER_ADMIN");
  for (String body : java.util.List.of("{}", "{\"name\":\"A\",\"email\":\"bad\",\"password\":\"long-password\"}", "{\"name\":\"A\",\"email\":\"a@example.test\",\"password\":\"short\"}"))
   mvc.perform(post("/api/super-admin/create-admin").header("Authorization","Bearer test-token").contentType("application/json").content(body))
    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.errors").isMap());
  verifyNoInteractions(admins);
 }
 @Test void statusRequiresExplicitSupportedValueAndPositiveId() throws Exception {
  token("SUPER_ADMIN");
  for (String body : java.util.List.of("{}", "{\"status\":\"WRONG\"}", "{\"status\":\"BLOCKED\"}"))
   mvc.perform(patch("/api/super-admin/admin/1/status").header("Authorization","Bearer test-token").contentType("application/json").content(body))
    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  mvc.perform(get("/api/super-admin/admin/0").header("Authorization","Bearer test-token")).andExpect(status().isBadRequest());
  verifyNoInteractions(admins);
 }
 @Test void notFoundConflictAndUnexpectedErrorsAreSafe() throws Exception {
  token("SUPER_ADMIN");
  when(admins.viewAdminById(1L)).thenThrow(SuperAdminApiException.notFound());
  when(admins.viewAdminById(2L)).thenThrow(SuperAdminApiException.duplicateEmail());
  when(admins.viewAdminById(3L)).thenThrow(new IllegalStateException("private SQL text"));
  when(admins.viewAdminById(4L)).thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate key uk_user_email_normalized private SQL"));
  mvc.perform(get("/api/super-admin/admin/1").header("Authorization","Bearer test-token")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ADMIN_NOT_FOUND"));
  mvc.perform(get("/api/super-admin/admin/2").header("Authorization","Bearer test-token")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
  mvc.perform(get("/api/super-admin/admin/3").header("Authorization","Bearer test-token")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value("Unable to complete the request. Please try again."));
  mvc.perform(get("/api/super-admin/admin/4").header("Authorization","Bearer test-token")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
 }
 @Test void pageBoundsAndRecentLimit() throws Exception {
  token("SUPER_ADMIN");
  when(logs.getSuperAdminLogs(0,10,"",null)).thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(), org.springframework.data.domain.PageRequest.of(0,10),0));
  mvc.perform(get("/api/super-admin/activity-logs?size=10").header("Authorization","Bearer test-token"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(10)).andExpect(jsonPath("$.totalElements").value(0));
  for (String query : java.util.List.of("size=101","size=0","page=-1","activityType=INVALID"))
   mvc.perform(get("/api/super-admin/activity-logs?"+query).header("Authorization","Bearer test-token"))
    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  verify(logs, times(1)).getSuperAdminLogs(anyInt(),anyInt(),anyString(),any());
 }

 @Test void createAndStatusPassNormalizedValidatedPayloadToService() throws Exception {
  token("SUPER_ADMIN");
  var actor = new com.ptc.halo.entity.UserEntity(); actor.setEmail("test@example.test"); actor.setRole(Role.SUPER_ADMIN);
  when(users.findByEmail("test@example.test")).thenReturn(java.util.Optional.of(actor));
  mvc.perform(post("/api/super-admin/create-admin").header("Authorization","Bearer test-token").contentType("application/json")
    .content("""
      {"name":"  New Admin  ","email":" NEW@EXAMPLE.TEST ","password":"test-password-12"}
      """))
    .andExpect(status().isOk());
  verify(admins).createAdmin(argThat(r -> r.getName().equals("New Admin") && r.getEmail().equals("new@example.test")),eq(actor));
  mvc.perform(patch("/api/super-admin/admin/7/status").header("Authorization","Bearer test-token").contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
    .andExpect(status().isOk());
  verify(admins).changeAdminStatus(7L,com.ptc.halo.enums.Status.ACTIVE,actor);
 }
}
