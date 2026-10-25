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
  when(jwt.extractUserId("token")).thenReturn(5L);when(jwt.isCurrent(eq("token"),any())).thenReturn(true);
  when(details.loadUserById(5L)).thenReturn(TestIdentity.details(5L, "actor@test", role));
  var u=new UserEntity();u.setId(5L);u.setRole(Role.valueOf(role));u.setStatus(Status.ACTIVE);when(users.findById(5L)).thenReturn(Optional.of(u));
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
  verify(users,atLeast(2)).findById(5L);verify(revisions).publish(41L,prepared,"validated",professor,null,false);
 }
 @Test void discardStillRequiresProfessorAndCurrentDraftEditToken()throws Exception{
  mvc.perform(delete(path).param("version","3")).andExpect(status().isUnauthorized());
  for(String role:List.of("STUDENT","ADMIN","SUPER_ADMIN")){
   token(role);mvc.perform(delete(path).param("version","3").header("Authorization","Bearer token")).andExpect(status().isForbidden());
  }
  verifyNoInteractions(revisions);var professor=token("PROFESSOR");
  mvc.perform(delete(path).param("version","3").header("Authorization","Bearer token")).andExpect(status().isNoContent());
  verify(revisions).discard(41L,3L,professor,null);
  doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"STALE_MODULE_OPERATION")).when(revisions).discard(41L,2L,professor,null);
  mvc.perform(delete(path).param("version","2").header("Authorization","Bearer token")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_MODULE_OPERATION"));
 }
 @Test void historyIsReadOnlyAndUsesAuthenticatedProfessor()throws Exception{
  mvc.perform(get(path+"/history")).andExpect(status().isUnauthorized());
  for(String role:List.of("STUDENT","ADMIN","SUPER_ADMIN")){
   token(role);mvc.perform(get(path+"/history/1").header("Authorization","Bearer token")).andExpect(status().isForbidden());
  }
  verifyNoInteractions(revisions);
  var professor=token("PROFESSOR");
  when(revisions.history(41L,professor,0,20)).thenReturn(org.springframework.data.domain.Page.empty());
  mvc.perform(get(path+"/history").param("professorId","99").header("Authorization","Bearer token")).andExpect(status().isOk());
  mvc.perform(get(path+"/history/1").header("Authorization","Bearer token")).andExpect(status().isOk());
  verify(revisions).history(41L,professor,0,20);verify(revisions).historyVersion(41L,1,professor);
  for(var method:List.of("PUT","POST","PATCH","DELETE"))
   mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method),path+"/history/1").header("Authorization","Bearer token")).andExpect(status().isMethodNotAllowed());
  when(revisions.historyVersion(42L,1,professor)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND"));
  mvc.perform(get(path.replace("41","42")+"/history/1").header("Authorization","Bearer token")).andExpect(status().isNotFound());
 }
 @Test void actingHistoryUsesTargetScopeWithoutChangingAdminIdentity()throws Exception{
  var admin=token("ADMIN");var target=new UserEntity();target.setId(9L);target.setRole(Role.PROFESSOR);
  var session=new AdminActingSessionEntity(UUID.randomUUID().toString(),admin,target,java.time.Instant.now(),java.time.Instant.now().plusSeconds(300));
  when(acting.execute(any(),eq(session.getId()),any())).thenAnswer(i->((AdminProfessorModeService.Work<?>)i.getArgument(2)).apply(session));
  mvc.perform(get("/api/admin/acting/professor/ai-learning-modules/41/revision/history/1").header("Authorization","Bearer token").header("X-Acting-Session",session.getId())).andExpect(status().isOk());
  verify(revisions).historyVersion(41L,1,target);
 }
}
