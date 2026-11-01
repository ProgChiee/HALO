package com.ptc.halo;
import com.ptc.halo.controller.*;import com.ptc.halo.config.SecurityConfig;import com.ptc.halo.security.*;import com.ptc.halo.service.*;import com.ptc.halo.repository.UserRepository;import com.ptc.halo.entity.*;import com.ptc.halo.enums.*;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;import org.springframework.boot.test.mock.mockito.MockBean;import org.springframework.context.annotation.Import;import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(value=LessonAttachmentController.class,properties="halo.cors.allowed-origins=https://halo.example.test")
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class LessonAttachmentHttpTest {
 @Autowired MockMvc mvc;@MockBean LessonAttachmentService files;@MockBean StudentLessonAccessService students;@MockBean UserRepository users;
 @MockBean AdminProfessorModeService professorMode;@MockBean AdminProfessorPreviewService professorPreview;@MockBean AdminStudentModeService studentMode;@MockBean AdminStudentPreviewService studentPreview;
 @MockBean CustomUserDetailsService details;@MockBean JwtService jwt;
 String session="350b29ca-a7d5-4a2d-a5d2-903db7e67e77";String route="/api/student/ai-learning-modules/files/5/content";
 UserEntity token(String role){when(jwt.extractUserId("token")).thenReturn(1L);when(jwt.isCurrent(eq("token"),any())).thenReturn(true);when(details.loadUserById(1L)).thenReturn(TestIdentity.details(1L,"synthetic@example.test",role));var actor=new UserEntity();actor.setId(1L);actor.setRole(Role.valueOf(role));actor.setStatus(Status.ACTIVE);when(users.findById(1L)).thenReturn(java.util.Optional.of(actor));return actor;}
 LessonAttachmentService.Content material(){return new LessonAttachmentService.Content("lesson.pdf","application/pdf",new byte[]{1,2});}
 @Test void authenticationIdentityAndResponseHeaders() throws Exception {
  mvc.perform(get(route)).andExpect(status().isUnauthorized());var actor=token("STUDENT");when(students.requireStudent(any())).thenReturn(actor);when(files.student(5L,actor)).thenReturn(material());
  mvc.perform(get(route).param("studentId","99").header("Authorization","Bearer token")).andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("inline;")));verify(files).student(5L,actor);
 }
 @Test void professorDownloadUsesAuthenticatedOwner() throws Exception {var actor=token("PROFESSOR");when(files.professor(5L,actor)).thenReturn(material());mvc.perform(get(route.replace("student","professor")).param("download","true").header("Authorization","Bearer token")).andExpect(status().isOk()).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;")));verify(files).professor(5L,actor);mvc.perform(get(route).header("Authorization","Bearer token")).andExpect(status().isForbidden());}
 @Test void invalidModesCannotReachStorage() throws Exception {token("SUPER_ADMIN");for(String kind:java.util.List.of("acting","preview"))mvc.perform(get("/api/admin/"+kind+"/student/ai-learning-modules/files/5/content").header("Authorization","Bearer token")).andExpect(status().isBadRequest());when(studentMode.execute(any(),eq(session),any())).thenThrow(new AdminApiException(AdminApiException.Code.ACTING_SESSION_INVALID));mvc.perform(get("/api/admin/acting/student/ai-learning-modules/files/5/content").header("Authorization","Bearer token").header("X-Acting-Session",session)).andExpect(status().isConflict());verifyNoInteractions(files);}
 @Test void previewUsesOnlyValidatedTargetAndCannotWrite() throws Exception {
  token("ADMIN");var target=new UserEntity();target.setId(77L);when(files.student(5L,target)).thenReturn(material());when(studentPreview.read(any(),eq(session),any())).thenAnswer(call->{java.util.function.Function<UserEntity,Object> work=call.getArgument(2);return work.apply(target);});
  String url="/api/admin/preview/student/ai-learning-modules/files/5/content";mvc.perform(get(url).header("Authorization","Bearer token").header("X-Acting-Session",session)).andExpect(status().isOk());verify(files).student(5L,target);mvc.perform(post(url).header("Authorization","Bearer token").header("X-Acting-Session",session)).andExpect(status().isMethodNotAllowed());
 }
 @Test void actingAndProfessorPreviewUseValidatedTarget() throws Exception {
  token("SUPER_ADMIN");var target=new UserEntity();target.setId(77L);var mode=mock(AdminActingSessionEntity.class);when(mode.getTarget()).thenReturn(target);
  when(files.student(5L,target)).thenReturn(material());when(files.professor(5L,target)).thenReturn(material());
  when(studentMode.execute(any(),eq(session),any())).thenAnswer(call->{AdminStudentModeService.Work<Object> work=call.getArgument(2);return work.apply(mode);});
  when(professorMode.execute(any(),eq(session),any())).thenAnswer(call->{AdminProfessorModeService.Work<Object> work=call.getArgument(2);return work.apply(mode);});
  when(professorPreview.read(any(),eq(session),any())).thenAnswer(call->{java.util.function.Function<AdminActingSessionEntity,Object> work=call.getArgument(2);return work.apply(mode);});
  for(String prefix:java.util.List.of("acting/student","acting/professor","preview/professor"))mvc.perform(get("/api/admin/"+prefix+"/ai-learning-modules/files/5/content").header("Authorization","Bearer token").header("X-Acting-Session",session)).andExpect(status().isOk());
  verify(files).student(5L,target);verify(files,times(2)).professor(5L,target);
 }
 @Test void configuredVercelOriginIsAllowedButUntrustedOriginIsNot() throws Exception {mvc.perform(options(route).header("Origin","https://halo.example.test").header("Access-Control-Request-Method","GET").header("Access-Control-Request-Headers","Authorization,X-Acting-Session")).andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","https://halo.example.test"));mvc.perform(options(route).header("Origin","https://untrusted.example.test").header("Access-Control-Request-Method","GET")).andExpect(status().isForbidden());}
}
