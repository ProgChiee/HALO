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
@WebMvcTest({StudentStudyCalendarController.class,LessonStudyController.class,ProfessorEngagementController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class EngagementHttpTest {
 @Autowired MockMvc mvc;
 @MockBean LessonStudyService study;
 @MockBean StudentStudyActivityService calendar;
 @MockBean AdminStudentPreviewService preview;
 @MockBean StudentLessonAccessService access;
 @MockBean AdminStudentModeService support;
 @MockBean ActivityLogService audit;
 @MockBean ProfessorEngagementService engagement;
 @MockBean AdminProfessorModeService acting;
 @MockBean UserRepository users;
 @MockBean CustomUserDetailsService details;
 @MockBean JwtService jwt;
 UserEntity token(String role){
  when(jwt.extractUserId("token")).thenReturn(5L);when(jwt.isCurrent(eq("token"),any())).thenReturn(true);
  when(details.loadUserById(5L)).thenReturn(TestIdentity.details(5L, "actor@test", role));
  var u=new UserEntity();u.setId(5L);u.setStatus(com.ptc.halo.enums.Status.ACTIVE);u.setRole(Role.valueOf(role));when(users.findById(5L)).thenReturn(Optional.of(u));when(access.requireStudent(any())).thenReturn(u);return u;
 }
 @Test void authenticatedProfessorIsTheOnlyScope() throws Exception {
  var u=token("PROFESSOR");mvc.perform(get("/api/professor/students/engagement").param("professorId","999").header("Authorization","Bearer token")).andExpect(status().isOk());verify(engagement).get(u,0,20,com.ptc.halo.enums.ProfessorStudentView.CURRENT);
 }
 @Test void rolesAndPreviewCannotRecordRealStudy() throws Exception {
  mvc.perform(post("/api/student/ai-learning-modules/week/3/study")).andExpect(status().isUnauthorized());
  token("ADMIN");mvc.perform(post("/api/student/ai-learning-modules/week/3/study").header("Authorization","Bearer token")).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/preview/student/ai-learning-modules/week/3/study").header("Authorization","Bearer token")).andExpect(status().isNotFound());
  token("STUDENT");mvc.perform(get("/api/professor/students/engagement").header("Authorization","Bearer token")).andExpect(status().isForbidden());verifyNoInteractions(study,engagement);
 }
 @Test void moduleMetricsUseAuthenticatedProfessorAndSerializeConsistency() throws Exception {
  var u=token("PROFESSOR");
  mvc.perform(get("/api/professor/students/10/engagement/modules").param("professorId","999").header("Authorization","Bearer token")).andExpect(status().isOk());verify(engagement).modules(u,10L,0,20,com.ptc.halo.enums.ProfessorStudentView.CURRENT);
  when(engagement.get(u,0,20,com.ptc.halo.enums.ProfessorStudentView.CURRENT)).thenReturn(new ProfessorEngagementService.Page(List.of(new ProfessorEngagementService.Engagement(10L,"Student",3,4,0,0)),0,20,1,1,true,true,"Asia/Manila","MONDAY"));
  mvc.perform(get("/api/professor/students/engagement").header("Authorization","Bearer token")).andExpect(jsonPath("$.content[0].weeklyConsistency").value("Consistent")).andExpect(jsonPath("$.content[0].monthlyConsistency").value("Moderate"));
  token("ADMIN");mvc.perform(get("/api/professor/students/10/engagement/modules").header("Authorization","Bearer token")).andExpect(status().isForbidden());
  token("STUDENT");mvc.perform(get("/api/admin/acting/professor/students/10/engagement/modules").header("Authorization","Bearer token")).andExpect(status().isForbidden());
 }
 @Test void actingModuleMetricsUseOnlyValidatedSessionTarget() throws Exception {
  token("ADMIN");var target=new UserEntity();target.setId(99L);target.setRole(Role.PROFESSOR);
  var session=mock(AdminActingSessionEntity.class);when(session.getTarget()).thenReturn(target);String id=UUID.randomUUID().toString();
  when(acting.execute(any(),eq(id),any())).thenAnswer(invocation->((AdminProfessorModeService.Work<?>)invocation.getArgument(2)).apply(session));
  mvc.perform(get("/api/admin/acting/professor/students/10/engagement/modules").param("professorId","777").header("X-Acting-Session",id).header("Authorization","Bearer token")).andExpect(status().isOk());verify(engagement).modules(target,10L,0,20,com.ptc.halo.enums.ProfessorStudentView.CURRENT);
  when(acting.execute(any(),eq(id),any())).thenThrow(new org.springframework.security.access.AccessDeniedException("Denied"));
  mvc.perform(get("/api/admin/acting/professor/students/10/engagement/modules").header("X-Acting-Session",id).header("Authorization","Bearer token")).andExpect(status().isForbidden());verifyNoMoreInteractions(engagement);
 }
 @Test void calendarUsesAuthenticatedStudentAndRejectsAdminOnNormalRoute() throws Exception {
  var u=token("STUDENT");mvc.perform(get("/api/student/study-calendar").param("month","2026-10").param("studentId","999").header("Authorization","Bearer token")).andExpect(status().isOk());verify(calendar).calendar(u,"2026-10");
  token("ADMIN");mvc.perform(get("/api/student/study-calendar").header("Authorization","Bearer token")).andExpect(status().isForbidden());
 }
 @Test void calendarActingErrorsRemainSafeAndSessionSpecific() throws Exception {
  token("ADMIN");String id=UUID.randomUUID().toString();
  for(var code:List.of(AdminApiException.Code.ACTING_SESSION_INVALID,AdminApiException.Code.INVALID_TARGET_ROLE,AdminApiException.Code.ACTING_TARGET_INACTIVE)){
   doThrow(new AdminApiException(code)).when(preview).read(any(),eq(id),any());doThrow(new AdminApiException(code)).when(support).execute(any(),eq(id),any());
   for(String mode:List.of("preview","acting"))mvc.perform(get("/api/admin/"+mode+"/student/study-calendar").header("Authorization","Bearer token").header("X-Acting-Session",id)).andExpect(status().is(code.status)).andExpect(jsonPath("$.code").value(code.name()));
  }
  mvc.perform(get("/api/admin/preview/student/study-calendar").header("Authorization","Bearer token")).andExpect(status().isBadRequest());verifyNoInteractions(calendar);
 }
 @Test void realStudentStudyUsesAuthenticatedIdentity() throws Exception {
  var u=token("STUDENT");mvc.perform(post("/api/student/ai-learning-modules/week/3/study").param("studentId","999").header("Authorization","Bearer token")).andExpect(status().isNoContent());verify(study).record(3L,u);
 }
}
