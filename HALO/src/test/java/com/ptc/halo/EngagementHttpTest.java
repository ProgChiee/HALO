package com.ptc.halo;
import com.ptc.halo.controller.*;
import com.ptc.halo.service.*;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.security.*;
import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.entity.*;
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
@WebMvcTest({LessonStudyController.class,ProfessorEngagementController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class EngagementHttpTest {
 @Autowired MockMvc mvc;
 @MockBean LessonStudyService study;
 @MockBean StudentLessonAccessService access;
 @MockBean AdminStudentModeService support;
 @MockBean ActivityLogService audit;
 @MockBean ProfessorEngagementService engagement;
 @MockBean AdminProfessorModeService acting;
 @MockBean UserRepository users;
 @MockBean CustomUserDetailsService details;
 @MockBean JwtService jwt;
 UserEntity token(String role){
  when(jwt.extractUsername("token")).thenReturn("actor@test");when(jwt.isCurrent(eq("token"),any())).thenReturn(true);
  when(details.loadUserByUsername("actor@test")).thenReturn(User.withUsername("actor@test").password("unused").roles(role).build());
  var u=new UserEntity();u.setId(5L);u.setRole(Role.valueOf(role));when(users.findByEmail("actor@test")).thenReturn(Optional.of(u));when(access.requireStudent(any())).thenReturn(u);return u;
 }
 @Test void authenticatedProfessorIsTheOnlyScope() throws Exception {
  var u=token("PROFESSOR");mvc.perform(get("/api/professor/students/engagement").param("professorId","999").header("Authorization","Bearer token")).andExpect(status().isOk());verify(engagement).get(u,0,20);
 }
 @Test void rolesAndPreviewCannotRecordRealStudy() throws Exception {
  mvc.perform(post("/api/student/ai-learning-modules/week/3/study")).andExpect(status().isUnauthorized());
  token("ADMIN");mvc.perform(post("/api/student/ai-learning-modules/week/3/study").header("Authorization","Bearer token")).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/preview/student/ai-learning-modules/week/3/study").header("Authorization","Bearer token")).andExpect(status().isNotFound());
  token("STUDENT");mvc.perform(get("/api/professor/students/engagement").header("Authorization","Bearer token")).andExpect(status().isForbidden());verifyNoInteractions(study,engagement);
 }
 @Test void moduleMetricsUseAuthenticatedProfessorAndSerializeConsistency() throws Exception {
  var u=token("PROFESSOR");
  mvc.perform(get("/api/professor/students/10/engagement/modules").param("professorId","999").header("Authorization","Bearer token")).andExpect(status().isOk());verify(engagement).modules(u,10L,0,20);
  when(engagement.get(u,0,20)).thenReturn(new ProfessorEngagementService.Page(List.of(new ProfessorEngagementService.Engagement(10L,"Student",3,4,0,0)),0,20,1,1,true,true,"Asia/Manila","MONDAY"));
  mvc.perform(get("/api/professor/students/engagement").header("Authorization","Bearer token")).andExpect(jsonPath("$.content[0].weeklyConsistency").value("Consistent")).andExpect(jsonPath("$.content[0].monthlyConsistency").value("Moderate"));
  token("ADMIN");mvc.perform(get("/api/professor/students/10/engagement/modules").header("Authorization","Bearer token")).andExpect(status().isForbidden());
  token("STUDENT");mvc.perform(get("/api/admin/acting/professor/students/10/engagement/modules").header("Authorization","Bearer token")).andExpect(status().isForbidden());
 }
 @Test void actingModuleMetricsUseOnlyValidatedSessionTarget() throws Exception {
  token("ADMIN");var target=new UserEntity();target.setId(99L);target.setRole(Role.PROFESSOR);
  var session=mock(AdminActingSessionEntity.class);when(session.getTarget()).thenReturn(target);String id=UUID.randomUUID().toString();
  when(acting.execute(any(),eq(id),any())).thenAnswer(invocation->((AdminProfessorModeService.Work<?>)invocation.getArgument(2)).apply(session));
  mvc.perform(get("/api/admin/acting/professor/students/10/engagement/modules").param("professorId","777").header("X-Acting-Session",id).header("Authorization","Bearer token")).andExpect(status().isOk());verify(engagement).modules(target,10L,0,20);
  when(acting.execute(any(),eq(id),any())).thenThrow(new org.springframework.security.access.AccessDeniedException("Denied"));
  mvc.perform(get("/api/admin/acting/professor/students/10/engagement/modules").header("X-Acting-Session",id).header("Authorization","Bearer token")).andExpect(status().isForbidden());verifyNoMoreInteractions(engagement);
 }
 @Test void realStudentStudyUsesAuthenticatedIdentity() throws Exception {
  var u=token("STUDENT");mvc.perform(post("/api/student/ai-learning-modules/week/3/study").param("studentId","999").header("Authorization","Bearer token")).andExpect(status().isNoContent());verify(study).record(3L,u);
 }
}
