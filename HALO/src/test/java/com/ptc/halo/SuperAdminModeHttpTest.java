package com.ptc.halo;
import com.ptc.halo.config.SecurityConfig;import com.ptc.halo.controller.*;import com.ptc.halo.security.*;import com.ptc.halo.service.*;import com.ptc.halo.entity.*;import com.ptc.halo.enums.*;import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;import org.springframework.boot.test.mock.mockito.MockBean;import org.springframework.context.annotation.Import;import org.springframework.test.web.servlet.MockMvc;import static org.mockito.Mockito.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest({SuperAdminAdminModeController.class,AdminActingController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class SuperAdminModeHttpTest {
 @Autowired MockMvc mvc;@MockBean SuperAdminAdminModeService mode;@MockBean AdminActingSessionService sessions;@MockBean AdminService accounts;@MockBean AdminDashboardService dashboard;@MockBean AdminProfessorMonitoringService professors;@MockBean AdminStudentMonitoringService students;@MockBean ActivityLogService audit;@MockBean ProfileService profiles;@MockBean CustomUserDetailsService details;@MockBean JwtService jwt;
 String id="350b29ca-a7d5-4a2d-a5d2-903db7e67e77";String base="/api/super-admin/acting/admin";
 void token(String role){when(jwt.extractUserId("token")).thenReturn(1L);when(jwt.isCurrent(eq("token"),any())).thenReturn(true);when(details.loadUserById(1L)).thenReturn(TestIdentity.details(1L,"synthetic@example.test",role));}
 @Test void onlySuperAdminCanUseAdminModeAndHeaderIsRequired() throws Exception {
  mvc.perform(get(base+"/dashboard")).andExpect(status().isUnauthorized());
  for(String role:java.util.List.of("ADMIN","PROFESSOR","STUDENT")){token(role);mvc.perform(get(base+"/dashboard").header("Authorization","Bearer token").header("X-Acting-Session",id)).andExpect(status().isForbidden());}
  token("SUPER_ADMIN");mvc.perform(get(base+"/dashboard").header("Authorization","Bearer token")).andExpect(status().isBadRequest());verifyNoInteractions(mode);
 }
 @Test void adminReadsUseSessionTargetWithoutReplacingAuthentication() throws Exception {
  token("SUPER_ADMIN");var target=new UserEntity();target.setId(8L);target.setRole(Role.ADMIN);
  when(mode.execute(any(),eq(id),any())).thenAnswer(call->{org.junit.jupiter.api.Assertions.assertEquals("ROLE_SUPER_ADMIN",((org.springframework.security.core.Authentication)call.getArgument(0)).getAuthorities().iterator().next().getAuthority());java.util.function.Function<UserEntity,Object> work=call.getArgument(2);return work.apply(target);});
  mvc.perform(get(base+"/profile").param("targetUserId","999").header("Authorization","Bearer token").header("X-Acting-Session",id)).andExpect(status().isOk());verify(profiles).getUserProfile(target);
  mvc.perform(get("/api/admin/dashboard").header("Authorization","Bearer token")).andExpect(status().isForbidden());
  for(String role:java.util.List.of("professor","student"))mvc.perform(get("/api/"+role+"/dashboard").header("Authorization","Bearer token")).andExpect(status().isForbidden());
 }
 @Test void invalidSessionsAndNestedRoutesFailSafely() throws Exception {
  token("SUPER_ADMIN");when(mode.execute(any(),eq(id),any())).thenThrow(new AdminApiException(AdminApiException.Code.ACTING_SESSION_INVALID));mvc.perform(get(base+"/students").header("Authorization","Bearer token").header("X-Acting-Session",id)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACTING_SESSION_INVALID"));verifyNoInteractions(accounts);
  mvc.perform(post(base+"/acting/sessions").header("Authorization","Bearer token").header("X-Acting-Session",id).contentType("application/json").content("{}" )).andExpect(status().isNotFound());
 }
 @Test void allModeCreationRetainsOriginalActorAndRejectsNestedRequest() throws Exception {
  token("SUPER_ADMIN");when(sessions.create(any(),any())).thenThrow(new AdminApiException(AdminApiException.Code.ACTING_MODE_ALREADY_ACTIVE));
  for(String role:java.util.List.of("ADMIN","PROFESSOR","STUDENT"))mvc.perform(post("/api/admin/acting/sessions").header("Authorization","Bearer token").contentType("application/json").content("{\"targetUserId\":8,\"targetRole\":\""+role+"\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACTING_MODE_ALREADY_ACTIVE"));
  verify(sessions,times(3)).create(argThat(a->a.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("ROLE_SUPER_ADMIN"))),any());
 }
 @Test void modeDiscoveryAndRecoveryRequireOriginalSuperAdmin() throws Exception {
  mvc.perform(get("/api/admin/acting/active")).andExpect(status().isUnauthorized());
  for(String role:java.util.List.of("ADMIN","PROFESSOR","STUDENT")){token(role);mvc.perform(get("/api/admin/acting/active").header("Authorization","Bearer token")).andExpect(status().isForbidden());mvc.perform(delete("/api/admin/acting/active/"+id).header("Authorization","Bearer token")).andExpect(status().isForbidden());}
  token("SUPER_ADMIN");when(sessions.activeModes(any())).thenReturn(java.util.List.of());mvc.perform(get("/api/admin/acting/active").header("Authorization","Bearer token")).andExpect(status().isOk()).andExpect(content().json("[]"));
  mvc.perform(delete("/api/admin/acting/active/"+id).header("Authorization","Bearer token")).andExpect(status().isNoContent());verify(sessions).exitActiveMode(argThat(a->a.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("ROLE_SUPER_ADMIN"))),eq(id));
 }

}
