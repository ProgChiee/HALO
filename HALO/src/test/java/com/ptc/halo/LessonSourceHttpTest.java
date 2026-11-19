package com.ptc.halo;
import com.ptc.halo.controller.LessonSourceController;
import com.ptc.halo.service.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.security.*;
import com.ptc.halo.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LessonSourceController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class LessonSourceHttpTest {
 @Autowired MockMvc mvc;
 @MockBean ProfessorModuleService modules;@MockBean AiLearningModuleRepository repository;
 @MockBean LessonRevisionService revisions;@MockBean LessonSourceScope sources;@MockBean UserRepository users;
 @MockBean AdminProfessorModeService acting;@MockBean ActivityLogService audit;
 @MockBean AiGenerationService generation;
 @MockBean CustomUserDetailsService details;@MockBean JwtService jwt;@MockBean org.springframework.transaction.PlatformTransactionManager manager;
 final String path="/api/professor/ai-learning-modules/41/sources";
 @Test void confirmationPassesExplicitModeAndSavesSnapshotWithoutGeneratingOrPublishing()throws Exception{
  var p=token("PROFESSOR");var m=new AiLearningModuleEntity();m.setId(41L);m.setStatus(LessonStatus.PENDING);
  org.springframework.test.util.ReflectionTestUtils.setField(m,"version",2L);
  var w=new WeekEntity();w.setSubject(new SubjectEntity());m.setWeek(w);
  m.setGeneratedKnowledge("Previous outline");
  when(modules.findModule(41L,p)).thenReturn(m);
  when(repository.findForMentorOpenById(41L)).thenReturn(Optional.of(m));
  String snapshot="{\"mode\":\"OBTL_EXPANSION\"}";
  when(sources.confirm(eq(m),anyList(),eq(5L),eq(LessonSourceScope.Mode.OBTL_EXPANSION))).thenReturn(snapshot);
  mvc.perform(post(path+"/confirm").header("Authorization","Bearer token").contentType("application/json")
    .content("{\"version\":2,\"mode\":\"OBTL_EXPANSION\",\"choices\":[{\"fileId\":3,\"sectionIds\":[\"chapter-one\"],\"instructional\":false}]}"))
    .andExpect(status().isOk());
  verify(sources).confirm(m,List.of(new LessonSourceScope.Choice(3L,List.of("chapter-one"),false)),5L,LessonSourceScope.Mode.OBTL_EXPANSION);
  verify(repository).saveAndFlush(m);
  org.junit.jupiter.api.Assertions.assertEquals(snapshot,m.getSourceScope());
  org.junit.jupiter.api.Assertions.assertNull(m.getGeneratedKnowledge());
  org.junit.jupiter.api.Assertions.assertEquals(LessonStatus.PENDING,m.getStatus());
  verifyNoInteractions(generation);
 }
 @Test void academicReviewCannotBypassRoleOwnershipAcknowledgmentOrVersion()throws Exception{
  String body="{\"version\":1,\"acknowledged\":true,\"lesson\":{\"objectives\":\"a\",\"knowledge\":\"b\",\"examples\":\"c\",\"summary\":\"d\"}}";
  mvc.perform(post(path+"/review").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
  for(String role:List.of("STUDENT","ADMIN","SUPER_ADMIN")){token(role);mvc.perform(post(path+"/review").header("Authorization","Bearer token").contentType("application/json").content(body)).andExpect(status().isForbidden());}
  var p=token("PROFESSOR");
  mvc.perform(post(path+"/review").header("Authorization","Bearer token").contentType("application/json").content("{\"version\":1}")).andExpect(status().isUnprocessableEntity());
  var m=new AiLearningModuleEntity();m.setId(41L);org.springframework.test.util.ReflectionTestUtils.setField(m,"version",2L);m.setStatus(LessonStatus.PENDING);
  var w=new WeekEntity();var s=new SubjectEntity();s.setSubjectName("Synthetic");w.setSubject(s);m.setWeek(w);when(modules.findModule(41L,p)).thenReturn(m);
  mvc.perform(post(path+"/review").header("Authorization","Bearer token").contentType("application/json").content(body)).andExpect(status().isConflict());
  verifyNoInteractions(generation,repository);
 }
 UserEntity token(String role){
  when(jwt.extractUserId("token")).thenReturn(5L);when(jwt.isCurrent(eq("token"),any())).thenReturn(true);
  when(details.loadUserById(5L)).thenReturn(TestIdentity.details(5L,"actor@test",role));
  var u=new UserEntity();u.setId(5L);u.setRole(Role.valueOf(role));u.setStatus(Status.ACTIVE);when(users.findById(5L)).thenReturn(Optional.of(u));
  when(manager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());return u;
 }
 @Test void onlyOwnedProfessorRouteCanExtractAndPreviewCannotWrite()throws Exception{
  mvc.perform(post(path+"/extract").contentType("application/json").content("{\"version\":1}")).andExpect(status().isUnauthorized());
  for(String role:List.of("STUDENT","ADMIN","SUPER_ADMIN")){token(role);mvc.perform(post(path+"/extract").header("Authorization","Bearer token").contentType("application/json").content("{\"version\":1}")).andExpect(status().isForbidden());}
  var p=token("PROFESSOR");when(modules.findModule(41L,p)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"NOT_OWNED"));
  mvc.perform(post(path+"/extract").header("Authorization","Bearer token").contentType("application/json").content("{\"version\":1}")).andExpect(status().isForbidden());
  mvc.perform(post(path+"/extract").header("Authorization","Bearer token").header("X-Acting-Session","preview").contentType("application/json").content("{\"version\":1}")).andExpect(status().isForbidden());
  verifyNoInteractions(sources,repository);
 }
 @Test void staleAndPublishedInitialModulesCannotBeChanged()throws Exception{
  var p=token("PROFESSOR");var m=new AiLearningModuleEntity();m.setId(41L);org.springframework.test.util.ReflectionTestUtils.setField(m,"version",2L);m.setStatus(LessonStatus.APPROVED);
  var w=new WeekEntity();var s=new SubjectEntity();s.setSubjectName("Synthetic");w.setSubject(s);m.setWeek(w);when(modules.findModule(41L,p)).thenReturn(m);
  mvc.perform(post(path+"/extract").header("Authorization","Bearer token").contentType("application/json").content("{\"version\":2}")).andExpect(status().isConflict());
  m.setStatus(LessonStatus.PENDING);
  mvc.perform(post(path+"/extract").header("Authorization","Bearer token").contentType("application/json").content("{\"version\":1}")).andExpect(status().isConflict());
  verifyNoInteractions(sources,repository);
 }
}
