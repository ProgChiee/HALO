package com.ptc.halo;
import com.ptc.halo.controller.ProfilePhotoController;
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
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(ProfilePhotoController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class StudentPhotoHttpTest {
 @Autowired MockMvc mvc;
 @MockBean ProfilePhotoService photos;
 @MockBean StudentLessonAccessService access;
 @MockBean AdminProfessorModeService professor;
 @MockBean AdminStudentModeService support;
 @MockBean AdminStudentPreviewService preview;
 @MockBean UserRepository users;
 @MockBean CustomUserDetailsService details;
 @MockBean JwtService jwt;
 UserEntity token(String role){
  when(jwt.extractUserId("token")).thenReturn(5L);when(jwt.isCurrent(eq("token"),any())).thenReturn(true);
  when(details.loadUserById(5L)).thenReturn(TestIdentity.details(5L, "actor@test", role));
  var u=new UserEntity();u.setId(5L);u.setRole(Role.valueOf(role));u.setStatus(Status.ACTIVE);when(users.findById(5L)).thenReturn(Optional.of(u));when(access.requireStudent(any())).thenReturn(u);return u;
 }
 @Test void studentUploadAndReadUseAuthenticatedIdNotClientTarget() throws Exception {
  token("STUDENT");var file=new MockMultipartFile("photo","photo.png","image/png",new byte[]{1});
  mvc.perform(multipart("/api/student/profile/photo").file(file).with(r->{r.setMethod("PUT");return r;}).param("studentId","999").header("Authorization","Bearer token")).andExpect(status().isNoContent());verify(photos).save(5L,file);
  when(photos.read(5L)).thenReturn(new byte[]{1});mvc.perform(get("/api/student/profile/photo").header("Authorization","Bearer token")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
 }
 @Test void unauthorizedAndOtherRolesCannotReadOrWriteNormalPhoto() throws Exception {
  mvc.perform(get("/api/student/profile/photo")).andExpect(status().isUnauthorized());
  for(String role:List.of("ADMIN","PROFESSOR")){token(role);mvc.perform(get("/api/student/profile/photo").header("Authorization","Bearer token")).andExpect(status().isForbidden());mvc.perform(put("/api/student/profile/photo").header("Authorization","Bearer token")).andExpect(status().isForbidden());}verifyNoInteractions(photos);
 }
 @Test void actingReadUsesValidatedTargetAndRejectsExpiredSession() throws Exception {
  token("ADMIN");var target=new UserEntity();target.setId(99L);var session=mock(AdminActingSessionEntity.class);when(session.getTarget()).thenReturn(target);String id=UUID.randomUUID().toString();
  when(support.execute(any(),eq(id),any())).thenAnswer(i->((AdminStudentModeService.Work<?>)i.getArgument(2)).apply(session));
  mvc.perform(get("/api/admin/acting/student/profile/photo").header("Authorization","Bearer token").header("X-Acting-Session",id)).andExpect(status().isNoContent());verify(photos).read(99L);
  doThrow(new AdminApiException(AdminApiException.Code.ACTING_SESSION_INVALID)).when(preview).read(any(),eq(id),any());
  mvc.perform(get("/api/admin/preview/student/profile/photo").header("Authorization","Bearer token").header("X-Acting-Session",id)).andExpect(jsonPath("$.code").value("ACTING_SESSION_INVALID"));verifyNoMoreInteractions(photos);
 }

 @Test void allRealRolesCanOnlyUpdateTheirOwnPhoto() throws Exception {
  mvc.perform(get("/api/profile/photo")).andExpect(status().isUnauthorized());
  var file=new MockMultipartFile("photo","photo.png","image/png",new byte[]{1});
  for(String role:List.of("SUPER_ADMIN","ADMIN","PROFESSOR","STUDENT")) {
   token(role);
   mvc.perform(multipart("/api/profile/photo").file(file).with(r->{r.setMethod("PUT");return r;}).param("userId","999").header("Authorization","Bearer token")).andExpect(status().isNoContent());
   mvc.perform(get("/api/profile/photo").param("userId","999").header("Authorization","Bearer token")).andExpect(status().isNoContent());
  }
  verify(photos,times(4)).save(5L,file);verify(photos,times(4)).read(5L);verifyNoMoreInteractions(photos);
 }
 @Test void actingHeaderCannotAuthorizeWritesAndInactiveUserIsBlocked() throws Exception {
  var user=token("ADMIN");var file=new MockMultipartFile("photo","photo.png","image/png",new byte[]{1});
  mvc.perform(multipart("/api/profile/photo").file(file).with(r->{r.setMethod("PUT");return r;}).header("Authorization","Bearer token").header("X-Acting-Session",UUID.randomUUID().toString())).andExpect(status().isForbidden());
  user.setStatus(Status.INACTIVE);
  mvc.perform(get("/api/profile/photo").header("Authorization","Bearer token")).andExpect(status().isForbidden());verifyNoInteractions(photos);
 }
 @Test void professorActingReadUsesTargetAndHasNoWriteEndpoint() throws Exception {
  token("ADMIN");String id=UUID.randomUUID().toString();var target=new UserEntity();target.setId(77L);var session=mock(AdminActingSessionEntity.class);when(session.getTarget()).thenReturn(target);
  when(professor.execute(any(),eq(id),any())).thenAnswer(i->((AdminProfessorModeService.Work<?>)i.getArgument(2)).apply(session));
  mvc.perform(get("/api/admin/acting/professor/profile/photo").param("userId","999").header("Authorization","Bearer token").header("X-Acting-Session",id)).andExpect(status().isNoContent());verify(photos).read(77L);
  mvc.perform(put("/api/admin/acting/professor/profile/photo").header("Authorization","Bearer token").header("X-Acting-Session",id)).andExpect(status().is4xxClientError());verifyNoMoreInteractions(photos);
 }
}
