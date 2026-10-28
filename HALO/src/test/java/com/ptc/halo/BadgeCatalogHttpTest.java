package com.ptc.halo;

import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.StudentBadgeController;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StudentBadgeController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,BadgeService.class})
class BadgeCatalogHttpTest {
 @Autowired MockMvc mvc;
 @Autowired BadgeService service;
 @MockBean StudentBadgeRepository badges;
 @MockBean StudentModuleProgressRepository progress;
 @MockBean AssessmentAttemptRepository attempts;
 @MockBean ActivityLogService audit;
 @MockBean UserRepository users;
 @MockBean CustomUserDetailsService details;
 @MockBean JwtService jwt;
 void token(Role role,Status status){
  var u=new UserEntity();u.setId(5L);u.setName("Synthetic");u.setEmail("synthetic@example.test");u.setRole(role);u.setStatus(status);
  when(jwt.extractUserId("test-token")).thenReturn(5L);when(jwt.isCurrent(eq("test-token"),any())).thenReturn(true);
  when(details.loadUserById(5L)).thenReturn(new CustomUserDetails(u));when(users.findById(5L)).thenReturn(Optional.of(u));
 }
 @Test void catalogContainsEveryDefinitionWithoutAccountDataOrWrites() throws Exception {
  token(Role.STUDENT,Status.ACTIVE);
  mvc.perform(get("/api/student/badges/catalog").header("Authorization","Bearer test-token"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(BadgeType.values().length))
   .andExpect(jsonPath("$[0].badgeType").value("FIRST_STEP"))
   .andExpect(jsonPath("$[0].badgeName").value("First Step"))
   .andExpect(jsonPath("$[0].description").value("Completed your first learning module."))
   .andExpect(jsonPath("$[0].studentId").doesNotExist()).andExpect(jsonPath("$[0].earnedAt").doesNotExist());
  var catalog=service.getCatalog();assertEquals(BadgeType.values().length,catalog.stream().map(v->v.badgeType()).distinct().count());
  for(var row:catalog){assertFalse(row.badgeName().isBlank());assertFalse(row.description().isBlank());assertEquals(row.badgeType().getBadgeName(),row.badgeName());}
  verifyNoInteractions(badges,progress,attempts,audit);
 }
 @Test void catalogRequiresActiveStudentAndCannotBeMutated() throws Exception {
  mvc.perform(get("/api/student/badges/catalog")).andExpect(status().isUnauthorized());
  for(Role role:List.of(Role.ADMIN,Role.PROFESSOR,Role.SUPER_ADMIN)){
   token(role,Status.ACTIVE);mvc.perform(get("/api/student/badges/catalog").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
  }
  for(Status status:List.of(Status.INACTIVE,Status.ARCHIVED,Status.BLOCKED)){
   token(Role.STUDENT,status);mvc.perform(get("/api/student/badges/catalog").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
  }
  token(Role.STUDENT,Status.ACTIVE);
  mvc.perform(post("/api/student/badges/catalog").header("Authorization","Bearer test-token")).andExpect(status().isMethodNotAllowed());
  verifyNoInteractions(badges,progress,attempts,audit);
 }
 @Test void earnedBadgesKeepOriginalStoredNamesAndDescriptions() throws Exception {
  token(Role.STUDENT,Status.ACTIVE);var b=new StudentBadgeEntity();b.setBadgeType(BadgeType.FIRST_STEP);b.setBadgeName("Preserved historical name");b.setDescription("Preserved historical description");b.setEarnedAt(java.time.LocalDateTime.of(2026,10,1,12,0));
  when(badges.findByStudentIdOrderByEarnedAtDesc(5L)).thenReturn(List.of(b));
  mvc.perform(get("/api/student/badges").header("Authorization","Bearer test-token")).andExpect(status().isOk())
    .andExpect(jsonPath("$[0].badgeName").value("Preserved historical name")).andExpect(jsonPath("$[0].description").value("Preserved historical description"));
  verify(badges,never()).save(any());verifyNoInteractions(progress,attempts,audit);
 }
}
