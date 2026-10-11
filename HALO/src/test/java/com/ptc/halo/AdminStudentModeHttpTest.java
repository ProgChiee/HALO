package com.ptc.halo;
import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.AdminStudentModeController;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
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

@WebMvcTest(AdminStudentModeController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class AdminStudentModeHttpTest {
 @Autowired MockMvc mvc;
 @MockBean AdminStudentModeService mode;
 @MockBean StudentDashboardService dashboard;
 @MockBean StudentSubjectService subjects;
 @MockBean StudentLearningProgressionService progression;
 @MockBean StudentLessonService lessons;
 @MockBean StudentProgressService progress;
 @MockBean BadgeService badges;
 @MockBean AssessmentService assessments;
 @MockBean MentorService mentor;
 @MockBean ProfileService profiles;
 @MockBean CustomUserDetailsService details;
 @MockBean JwtService jwt;
 final String path="/api/admin/acting/student";
 final String id="350b29ca-a7d5-4a2d-a5d2-903db7e67e77";
 void token(String role){when(jwt.extractUsername("test-token")).thenReturn("admin@example.test");when(jwt.isCurrent(eq("test-token"),any())).thenReturn(true);when(details.loadUserByUsername("admin@example.test")).thenReturn(User.withUsername("admin@example.test").password("unused").roles(role).build());}
 @Test void adminRequestUsesGuardWithOriginalPrincipalAndHeader() throws Exception {
  token("ADMIN");when(mode.execute(any(),eq(id),any())).thenReturn(java.util.Map.of("ok",true));
  mvc.perform(get(path+"/subjects").param("studentId","999").header("X-Acting-Session",id).header("Authorization","Bearer test-token")).andExpect(status().isOk());
  verify(mode).execute(argThat(a->a.getName().equals("admin@example.test")&&a.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("ROLE_ADMIN"))),eq(id),any());
 }
 @Test void missingSessionInvalidBodyAndWrongRolesFailBeforeWorkflow() throws Exception {
  token("ADMIN");mvc.perform(get(path+"/subjects").header("Authorization","Bearer test-token")).andExpect(status().isBadRequest());
  mvc.perform(post(path+"/assessment/submit/1").header("Authorization","Bearer test-token").header("X-Acting-Session",id).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
  for(String role:java.util.List.of("PROFESSOR","STUDENT","SUPER_ADMIN")){token(role);mvc.perform(get(path+"/subjects").header("Authorization","Bearer test-token").header("X-Acting-Session",id)).andExpect(status().isForbidden());}
  mvc.perform(get(path+"/subjects").header("X-Acting-Session",id)).andExpect(status().isUnauthorized());verifyNoInteractions(mode);
 }
 @Test void sessionBusinessErrorsAreNotMasked() throws Exception {
  token("ADMIN");for(var code:java.util.List.of(AdminApiException.Code.ACTING_SESSION_INVALID,AdminApiException.Code.INVALID_TARGET_ROLE,AdminApiException.Code.RESOURCE_NOT_FOUND)){
   when(mode.execute(any(),eq(id),any())).thenThrow(new AdminApiException(code));
   mvc.perform(get(path+"/subjects").header("Authorization","Bearer test-token").header("X-Acting-Session",id)).andExpect(status().is(code.status)).andExpect(jsonPath("$.code").value(code.name()));
  }
 }
 @Test void adminStillCannotUseNormalStudentRoute() throws Exception {
  token("ADMIN");mvc.perform(get("/api/student/subjects").header("Authorization","Bearer test-token").header("X-Acting-Session",id)).andExpect(status().isForbidden());verifyNoInteractions(mode);
 }
 @Test void noCredentialMutationEndpointIsExposed() {
  for(var method:AdminStudentModeController.class.getDeclaredMethods()) {
   var put=method.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class);
   var post=method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
   if(put!=null)for(String value:put.value())org.junit.jupiter.api.Assertions.assertFalse(value.contains("profile")||value.contains("password")||value.contains("generate"));
   if(post!=null)for(String value:post.value())org.junit.jupiter.api.Assertions.assertFalse(value.contains("profile")||value.contains("password"));
  }
 }

 @Test void safeValidationAndUnexpectedFailures() throws Exception {
  token("ADMIN");
  mvc.perform(get(path+"/assessment/-1").header("Authorization","Bearer test-token").header("X-Acting-Session",id)).andExpect(status().isBadRequest());
  when(mode.execute(any(),eq(id),any())).thenThrow(new IllegalStateException("SQL secret"));
  mvc.perform(get(path+"/subjects").header("Authorization","Bearer test-token").header("X-Acting-Session",id))
   .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
 }
}
