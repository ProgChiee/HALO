package com.ptc.halo;
import com.ptc.halo.controller.SubjectCoverController;
import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubjectCoverController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class SubjectCoverHttpTest {
 @Autowired MockMvc mvc;
 @MockBean SubjectCoverService covers;@MockBean UserRepository users;@MockBean StudentLessonAccessService access;
 @MockBean AdminProfessorModeService professorMode;@MockBean AdminProfessorPreviewService professorPreview;@MockBean AdminStudentModeService studentMode;@MockBean AdminStudentPreviewService studentPreview;
 @MockBean CustomUserDetailsService details;@MockBean JwtService jwt;
 String session="350b29ca-a7d5-4a2d-a5d2-903db7e67e77";
 UserEntity token(String role){when(jwt.extractUserId("token")).thenReturn(1L);when(jwt.isCurrent(eq("token"),any())).thenReturn(true);when(details.loadUserById(1L)).thenReturn(TestIdentity.details(1L,"synthetic@example.test",role));var actor=new UserEntity();actor.setId(1L);actor.setRole(Role.valueOf(role));actor.setStatus(com.ptc.halo.enums.Status.ACTIVE);when(users.findById(1L)).thenReturn(java.util.Optional.of(actor));return actor;}
 @Test void normalReadsAreAuthenticatedAndMissingCustomIs204() throws Exception {
  mvc.perform(get("/api/professor/subjects/7/cover")).andExpect(status().isUnauthorized());var actor=token("PROFESSOR");
  mvc.perform(get("/api/professor/subjects/7/cover").header("Authorization","Bearer token")).andExpect(status().isNoContent()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"));verify(covers).professorCover(actor,7L);
 }
 @Test void writesUseAuthenticatedOwnerAndStudentsCannotUpload() throws Exception {
  var file=new MockMultipartFile("image","cover.png","image/png",new byte[]{1});var actor=token("PROFESSOR");
  mvc.perform(multipart("/api/professor/subjects/7/cover").file(file).with(r->{r.setMethod("PUT");return r;}).param("professorId","999").header("Authorization","Bearer token")).andExpect(status().isNoContent());verify(covers).change(eq(actor),eq(7L),any(),isNull());
  token("STUDENT");mvc.perform(delete("/api/professor/subjects/7/cover").header("Authorization","Bearer token")).andExpect(status().isForbidden());
 }
 @Test void previewCannotWriteAndModeReadsRequireScopedSession() throws Exception {
  token("SUPER_ADMIN");
  mvc.perform(delete("/api/admin/preview/professor/subjects/7/cover").header("Authorization","Bearer token").header("X-Acting-Session",session)).andExpect(status().isMethodNotAllowed());
  mvc.perform(get("/api/admin/preview/professor/subjects/7/cover").header("Authorization","Bearer token")).andExpect(status().isBadRequest());
  when(professorPreview.read(any(),eq(session),any())).thenThrow(new AdminApiException(AdminApiException.Code.ACTING_SESSION_INVALID));
  mvc.perform(get("/api/admin/preview/professor/subjects/7/cover").header("Authorization","Bearer token").header("X-Acting-Session",session)).andExpect(status().isConflict());verifyNoInteractions(covers);
 }
 @Test void studentSupportAndPreviewNeverGainCoverWrites() throws Exception {
  token("ADMIN");
  for(String kind:java.util.List.of("acting","preview"))mvc.perform(delete("/api/admin/"+kind+"/student/subjects/7/cover").header("Authorization","Bearer token").header("X-Acting-Session",session)).andExpect(status().isMethodNotAllowed());verifyNoInteractions(covers);
 }
}
