package com.ptc.halo;
import com.ptc.halo.controller.QuizRevisionController;
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
@WebMvcTest(QuizRevisionController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class QuizRevisionHttpTest {
 @Autowired MockMvc mvc;
 @MockBean QuizRevisionService revisions;@MockBean UserRepository users;@MockBean AdminProfessorModeService acting;
 @MockBean CustomUserDetailsService details;@MockBean JwtService jwt;@MockBean org.springframework.transaction.PlatformTransactionManager manager;
 final String path="/api/professor/ai-learning-modules/41/quiz-revision";
 UserEntity token(String role){
  when(jwt.extractUserId("token")).thenReturn(5L);when(jwt.isCurrent(eq("token"),any())).thenReturn(true);
  when(details.loadUserById(5L)).thenReturn(TestIdentity.details(5L, "actor@test", role));
  var u=new UserEntity();u.setId(5L);u.setRole(Role.valueOf(role));u.setStatus(Status.ACTIVE);when(users.findById(5L)).thenReturn(Optional.of(u));
  when(manager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());return u;
 }

 @Test void previewAndUnauthorizedRolesCannotGenerateOrApprove()throws Exception{
  mvc.perform(post(path+"/generate")).andExpect(status().isUnauthorized());
  for(String role:List.of("STUDENT","ADMIN","SUPER_ADMIN")){token(role);mvc.perform(post(path+"/generate").header("Authorization","Bearer token")).andExpect(status().isForbidden());}
  token("PROFESSOR");mvc.perform(post(path+"/generate").header("Authorization","Bearer token").header("X-Acting-Session",UUID.randomUUID())).andExpect(status().isForbidden());
  token("ADMIN");when(acting.execute(any(),eq("preview"),any())).thenThrow(new org.springframework.security.access.AccessDeniedException("Preview is read only"));
  for(String operation:List.of("generate","approve"))mvc.perform(post("/api/admin/acting/professor/ai-learning-modules/41/quiz-revision/"+operation).header("Authorization","Bearer token").header("X-Acting-Session","preview").contentType("application/json").content("{\"draftId\":2}")).andExpect(status().isForbidden());
  verifyNoInteractions(revisions);
 }
 @Test void professorGenerationRevalidatesAndApprovalRequiresExplicitDraft()throws Exception{
  var professor=token("PROFESSOR");var captured=new QuizRevisionService.Prepared(1L,2L,1,new AiLearningModuleEntity());var generated=new com.ptc.halo.dtoResponse.GeneratedAssessment();
  when(revisions.prepare(41L,professor)).thenReturn(captured);when(revisions.generate(captured)).thenReturn(generated);
  mvc.perform(post(path+"/generate").header("Authorization","Bearer token")).andExpect(status().isOk());verify(revisions).save(41L,captured,generated,professor,null);verify(users,atLeast(2)).findById(5L);
  mvc.perform(post(path+"/approve").header("Authorization","Bearer token").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
  mvc.perform(post(path+"/approve").header("Authorization","Bearer token").contentType("application/json").content("{\"draftId\":7}")).andExpect(status().isOk());verify(revisions).approve(41L,7L,professor,null);
 }
 @Test void invalidAiResponseReturnsActionableErrorWithoutPersisting()throws Exception{
  var professor=token("PROFESSOR");var captured=new QuizRevisionService.Prepared(1L,2L,1,new AiLearningModuleEntity());
  when(revisions.prepare(41L,professor)).thenReturn(captured);
  when(revisions.generate(captured)).thenThrow(GeneratedAssessmentValidator.invalid());
  mvc.perform(post(path+"/generate").header("Authorization","Bearer token"))
   .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("QUIZ_GENERATION_INVALID"))
   .andExpect(jsonPath("$.message").value("The AI returned an incomplete or invalid quiz. Please retry quiz generation or lesson approval. No quiz was saved."));
  verify(revisions,never()).save(any(),any(),any(),any(),any());
 }
}
