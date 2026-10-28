package com.ptc.halo;
import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.StudentEnrollmentController;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StudentEnrollmentController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,StudentLessonAccessService.class})
class StudentEnrollmentHttpTest {
 @Autowired MockMvc mvc;
 @MockBean StudentEnrollmentService service;
 @MockBean StudentCurriculumService curriculum;
 @MockBean UserRepository users;
 @MockBean StudentProfileRepository profiles;
 @MockBean StudentSubjectEnrollmentRepository enrollments;
 @MockBean AdminStudentPreviewService preview;
 @MockBean AdminStudentModeService support;
 @MockBean CustomUserDetailsService details;
 @MockBean JwtService jwt;
 UserEntity student;
 @BeforeEach void setup(){student=new UserEntity();student.setId(5L);student.setName("Synthetic");student.setRole(Role.STUDENT);student.setStatus(Status.ACTIVE);when(users.findById(5L)).thenReturn(Optional.of(student));when(jwt.extractUserId("test-token")).thenReturn(5L);when(jwt.isCurrent(eq("test-token"),any())).thenReturn(true);role("STUDENT");}
 void role(String role){when(details.loadUserById(5L)).thenReturn(TestIdentity.details(5L,"old-email@example.test",role));}
 @Test void ownListsUseImmutableIdentityAndEnrollmentIgnoresSpoofedUserIds() throws Exception {
  for(String view:List.of("my","browse"))mvc.perform(get("/api/student/subjects/"+view).header("Authorization","Bearer test-token").param("studentId","999")).andExpect(status().isOk());
  verify(service).list(student,true);verify(service).list(student,false);
  mvc.perform(post("/api/student/subjects/15/enrollment").header("Authorization","Bearer test-token").contentType("application/json").content("{\"studentId\":999}")).andExpect(status().isOk());
  verify(service).enroll(argThat(a->((CustomUserDetails)a.getPrincipal()).getUserId().equals(5L)),eq(15L));verify(users,never()).findById(999L);verify(users,never()).findByEmail(anyString());
 }
 @Test void unauthorizedRolesAndActingCannotWrite() throws Exception {
  mvc.perform(get("/api/student/subjects/my")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/student/subjects/15/enrollment")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/student/subjects/15/enrollment").header("Authorization","Bearer test-token").header("X-Acting-Session",UUID.randomUUID())).andExpect(status().isForbidden());
  for(String role:List.of("PROFESSOR","ADMIN","SUPER_ADMIN")){role(role);mvc.perform(post("/api/student/subjects/15/enrollment").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());mvc.perform(get("/api/student/subjects/my").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());}
  verify(service,never()).enroll(any(),any());
 }
 @Test void previewSupportReadsRequireValidOwnedSessionAndHaveNoWriteEndpoint() throws Exception {
  role("ADMIN");String session=UUID.randomUUID().toString();
  for(String base:List.of("/api/admin/preview/student","/api/admin/acting/student")){
   mvc.perform(get(base+"/subjects/my").header("Authorization","Bearer test-token").header("X-Acting-Session",session)).andExpect(status().isOk());
   mvc.perform(post(base+"/subjects/15/enrollment").header("Authorization","Bearer test-token").header("X-Acting-Session",session)).andExpect(status().isNotFound());
  }
  verify(preview).read(any(),eq(session),any());verify(support).execute(any(),eq(session),any());
  doThrow(new AdminApiException(AdminApiException.Code.ACTING_SESSION_INVALID)).when(preview).read(any(),eq(session),any());
  doThrow(new AdminApiException(AdminApiException.Code.ACTING_SESSION_INVALID)).when(support).execute(any(),eq(session),any());
  for(String base:List.of("/api/admin/preview/student","/api/admin/acting/student"))mvc.perform(get(base+"/subjects/browse").header("Authorization","Bearer test-token").header("X-Acting-Session",session)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACTING_SESSION_INVALID"));
  verify(service,never()).enroll(any(),any());
 }
 @Test void inactiveAccountsAndIncompleteProfilesHaveSafeErrors() throws Exception {
  student.setStatus(Status.INACTIVE);mvc.perform(get("/api/student/subjects/my").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
  student.setStatus(Status.ACTIVE);when(service.list(student,true)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"PROFILE_INCOMPLETE"));
  mvc.perform(get("/api/student/subjects/my").header("Authorization","Bearer test-token")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROFILE_INCOMPLETE"));
 }

 @Test void curriculumRolesIdentityAndModeSessionGuards() throws Exception {
  var dto=new com.ptc.halo.dtoResponse.StudentCurriculumResponse(15L,"HRM101","Public title",YearLevel.FIRST_YEAR,1,List.of(new com.ptc.halo.dtoResponse.StudentCurriculumResponse.PublishedWeek(1,"Published lesson")));
  when(curriculum.preview(student,15L)).thenReturn(dto);
  mvc.perform(get("/api/student/subjects/15/curriculum")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/student/subjects/15/curriculum").header("Authorization","Bearer test-token").param("studentId","999")).andExpect(status().isOk()).andExpect(jsonPath("$.weeks[0].title").value("Published lesson")).andExpect(jsonPath("$.lessonText").doesNotExist());
  verify(curriculum).preview(student,15L);
  for(String role:List.of("PROFESSOR","ADMIN","SUPER_ADMIN")){role(role);mvc.perform(get("/api/student/subjects/15/curriculum").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());}
  role("ADMIN");String session=UUID.randomUUID().toString();
  when(preview.read(any(),eq(session),any())).thenAnswer(inv->((java.util.function.Function<UserEntity,Object>)inv.getArgument(2)).apply(student));
  var acting=mock(com.ptc.halo.entity.AdminActingSessionEntity.class);when(acting.getTarget()).thenReturn(student);
  when(support.execute(any(),eq(session),any())).thenAnswer(inv->((AdminStudentModeService.Work<Object>)inv.getArgument(2)).apply(acting));
  for(String base:List.of("/api/admin/preview/student","/api/admin/acting/student")){
   mvc.perform(get(base+"/subjects/15/curriculum").header("Authorization","Bearer test-token").header("X-Acting-Session",session)).andExpect(status().isOk()).andExpect(jsonPath("$.subjectId").value(15));
   mvc.perform(get(base+"/subjects/15/curriculum").header("Authorization","Bearer test-token")).andExpect(status().isBadRequest());
  }
  doThrow(new AdminApiException(AdminApiException.Code.ACTING_SESSION_INVALID)).when(preview).read(any(),eq(session),any());
  doThrow(new AdminApiException(AdminApiException.Code.ACTING_SESSION_INVALID)).when(support).execute(any(),eq(session),any());
  for(String base:List.of("/api/admin/preview/student","/api/admin/acting/student"))mvc.perform(get(base+"/subjects/15/curriculum").header("Authorization","Bearer test-token").header("X-Acting-Session",session)).andExpect(status().isConflict());
  verify(service,never()).enroll(any(),any());
 }
}
