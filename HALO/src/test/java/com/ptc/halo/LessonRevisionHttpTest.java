package com.ptc.halo;
import com.ptc.halo.controller.LessonRevisionController;
import com.ptc.halo.service.*;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.security.*;
import com.ptc.halo.config.SecurityConfig;
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
@WebMvcTest(LessonRevisionController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class LessonRevisionHttpTest {
 @Autowired MockMvc mvc;
 @MockBean LessonRevisionService revisions;@MockBean UserRepository users;@MockBean AdminProfessorModeService acting;
 @MockBean CustomUserDetailsService details;@MockBean JwtService jwt;@MockBean org.springframework.transaction.PlatformTransactionManager manager;
 final String path="/api/professor/ai-learning-modules/41/revision";
 UserEntity token(String role){
  when(jwt.extractUsername("token")).thenReturn("actor@test");when(jwt.isCurrent(eq("token"),any())).thenReturn(true);
  when(details.loadUserByUsername("actor@test")).thenReturn(User.withUsername("actor@test").password("unused").roles(role).build());
  var u=new UserEntity();u.setId(5L);u.setRole(Role.valueOf(role));u.setStatus(Status.ACTIVE);when(users.findByEmail("actor@test")).thenReturn(Optional.of(u));
  when(manager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());return u;
 }
 @Test void onlyAuthenticatedProfessorMayUseNormalRevisionRoute()throws Exception{
  mvc.perform(post(path)).andExpect(status().isUnauthorized());
  for(String role:List.of("STUDENT","ADMIN","SUPER_ADMIN")){token(role);mvc.perform(post(path).header("Authorization","Bearer token")).andExpect(status().isForbidden());}
  verifyNoInteractions(revisions);
  var professor=token("PROFESSOR");mvc.perform(post(path).header("Authorization","Bearer token")).andExpect(status().isOk());verify(revisions).start(41L,professor,null);
  mvc.perform(post(path).header("Authorization","Bearer token").header("X-Acting-Session",UUID.randomUUID())).andExpect(status().isForbidden());
 }
 @Test void actingUsesValidatedTargetAndPreviewCredentialsCannotWrite()throws Exception{
  var admin=token("ADMIN");var target=new UserEntity();target.setId(9L);target.setRole(Role.PROFESSOR);
  var session=new AdminActingSessionEntity(UUID.randomUUID().toString(),admin,target,java.time.Instant.now(),java.time.Instant.now().plusSeconds(300));
  when(acting.execute(any(),eq(session.getId()),any())).thenAnswer(i->((AdminProfessorModeService.Work<?>)i.getArgument(2)).apply(session));
  String route="/api/admin/acting/professor/ai-learning-modules/41/revision";
  mvc.perform(post(route).header("Authorization","Bearer token").header("X-Acting-Session",session.getId())).andExpect(status().isOk());verify(revisions).start(41L,target,session);
  when(acting.execute(any(),eq("preview-session"),any())).thenThrow(new org.springframework.security.access.AccessDeniedException("Invalid session"));
  mvc.perform(post(route).header("Authorization","Bearer token").header("X-Acting-Session","preview-session")).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/preview/professor/ai-learning-modules/41/revision").header("Authorization","Bearer token")).andExpect(status().isNotFound());
  verifyNoMoreInteractions(revisions);
 }
 @Test void publishRequiresVersionAndRevalidatesAccessAfterPreparation()throws Exception{
  var professor=token("PROFESSOR");
  mvc.perform(post(path+"/publish").header("Authorization","Bearer token").contentType("application/json").content("{}")) .andExpect(status().isBadRequest());verifyNoInteractions(revisions);
  var prepared=new LessonRevisionService.Prepared(2L,1L,new AiLearningModuleEntity());when(revisions.prepare(41L,2L,professor,true)).thenReturn(prepared);when(revisions.fingerprint(prepared)).thenReturn("validated");
  mvc.perform(post(path+"/publish").header("Authorization","Bearer token").contentType("application/json").content("{\"version\":2}")) .andExpect(status().isNoContent());
  verify(users,atLeast(2)).findByEmail("actor@test");verify(revisions).publish(41L,prepared,"validated",professor,null,false);
 }
}
