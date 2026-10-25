package com.ptc.halo;
import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.AdminProfessorPreviewController;
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

@WebMvcTest(AdminProfessorPreviewController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class AdminProfessorPreviewHttpTest {
 @Autowired MockMvc mvc;
 @MockBean AdminProfessorPreviewService mode;
 @MockBean ProfessorAcademicService academic;
 @MockBean ProfessorModuleService modules;
 @MockBean ProfessorDashboardService dashboard;
 @MockBean ProfessorStudentService students;
 @MockBean ProfileService profiles;
 @MockBean ProfessorEngagementService engagement;
 @MockBean ProfilePhotoService photos;
 @MockBean CustomUserDetailsService details;
 @MockBean JwtService jwt;
 final String path="/api/admin/preview/professor";
 final String id="350b29ca-a7d5-4a2d-a5d2-903db7e67e77";
 void token(String role){when(jwt.extractUserId("test-token")).thenReturn(1L);when(jwt.isCurrent(eq("test-token"),any())).thenReturn(true);when(details.loadUserById(1L)).thenReturn(User.withUsername("admin@example.test").password("unused").roles(role).build());}
 @Test void adminRequestUsesGuardWithOriginalPrincipalAndHeader() throws Exception {
  token("ADMIN");when(mode.read(any(),eq(id),any())).thenReturn(java.util.Map.of("ok",true));
  mvc.perform(get(path+"/subjects").param("professorId","999").header("X-Acting-Session",id).header("Authorization","Bearer test-token")).andExpect(status().isOk());
  verify(mode).read(argThat(a->a.getName().equals("admin@example.test")&&a.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("ROLE_ADMIN"))),eq(id),any());
 }
 @Test void missingSessionInvalidBodyAndWrongRolesFailBeforeWorkflow() throws Exception {
  token("ADMIN");mvc.perform(get(path+"/subjects").header("Authorization","Bearer test-token")).andExpect(status().isBadRequest());
  for(String role:java.util.List.of("PROFESSOR","STUDENT","SUPER_ADMIN")){token(role);mvc.perform(get(path+"/subjects").header("Authorization","Bearer test-token").header("X-Acting-Session",id)).andExpect(status().isForbidden());}
  mvc.perform(get(path+"/subjects").header("X-Acting-Session",id)).andExpect(status().isUnauthorized());verifyNoInteractions(mode);
 }
 @Test void sessionBusinessErrorsAreNotMasked() throws Exception {
  token("ADMIN");for(var code:java.util.List.of(AdminApiException.Code.ACTING_SESSION_INVALID,AdminApiException.Code.INVALID_TARGET_ROLE,AdminApiException.Code.RESOURCE_NOT_FOUND)){
   when(mode.read(any(),eq(id),any())).thenThrow(new AdminApiException(code));
   mvc.perform(get(path+"/subjects").header("Authorization","Bearer test-token").header("X-Acting-Session",id)).andExpect(status().is(code.status)).andExpect(jsonPath("$.code").value(code.name()));
  }
 }
 @Test void adminStillCannotUseNormalProfessorRoute() throws Exception {
  token("ADMIN");mvc.perform(get("/api/professor/subjects").header("Authorization","Bearer test-token").header("X-Acting-Session",id)).andExpect(status().isForbidden());verifyNoInteractions(mode);
 }
 @Test void studentReadsUseSessionTargetScopeAndPropagateDenial() throws Exception {
  token("ADMIN");
  var target=new com.ptc.halo.entity.UserEntity();target.setId(21L);target.setRole(com.ptc.halo.enums.Role.PROFESSOR);
  var session=mock(com.ptc.halo.entity.AdminActingSessionEntity.class);when(session.getTarget()).thenReturn(target);
  when(mode.read(any(),eq(id),any())).thenAnswer(call->{java.util.function.Function<com.ptc.halo.entity.AdminActingSessionEntity,Object> work=call.getArgument(2);return work.apply(session);});
  for(String suffix:java.util.List.of("","/10/progress","/10/subjects","/10/assessments","/10/badges"))
   mvc.perform(get(path+"/students"+suffix).param("professorId","999").header("Authorization","Bearer test-token").header("X-Acting-Session",id)).andExpect(status().isOk());
  verify(students).getStudents(target);verify(students).getStudentProgress(10L,target);verify(students).getStudentSubjects(10L,target);verify(students).getStudentAssessmentHistory(10L,target);verify(students).getStudentBadges(10L,target);
  var denied=new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND");
  when(students.getStudentProgress(99L,target)).thenThrow(denied);when(students.getStudentSubjects(99L,target)).thenThrow(denied);when(students.getStudentAssessmentHistory(99L,target)).thenThrow(denied);when(students.getStudentBadges(99L,target)).thenThrow(denied);
  for(String suffix:java.util.List.of("progress","subjects","assessments","badges"))
   mvc.perform(get(path+"/students/99/"+suffix).header("Authorization","Bearer test-token").header("X-Acting-Session",id)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
 }
 @Test void allPreviewWritesAreBlockedBeforeBusinessServices() throws Exception {
  token("ADMIN");when(mode.read(any(),eq(id),any())).thenAnswer(call->{java.util.function.Function<com.ptc.halo.entity.AdminActingSessionEntity,Object> work=call.getArgument(2);return work.apply(null);});
  for(String method:java.util.List.of("POST","PUT","PATCH","DELETE")) for(String endpoint:java.util.List.of("/subjects","/ai-learning-modules/1/generate","/profile","/students/1/progress"))
   mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method),path+endpoint).header("Authorization","Bearer test-token").header("X-Acting-Session",id)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PREVIEW_READ_ONLY"));
  verifyNoInteractions(academic,modules,students,photos);
 }
 @Test void noCredentialMutationEndpointIsExposed() {
  for(var method:AdminProfessorPreviewController.class.getDeclaredMethods()) {
   var put=method.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class);
   var post=method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
   if(put!=null)for(String value:put.value())org.junit.jupiter.api.Assertions.assertFalse(value.contains("profile")||value.contains("password"));
   if(post!=null)for(String value:post.value())org.junit.jupiter.api.Assertions.assertFalse(value.contains("profile")||value.contains("password"));
  }
 }
}
