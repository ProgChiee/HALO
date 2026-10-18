package com.ptc.halo;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"},showSql=false)
@Import({SelfProfileService.class,ActivityLogService.class})
class SelfProfileTest {
 @Autowired SelfProfileService service; @Autowired UserRepository users; @Autowired ActivityLogRepository logs;
 @Autowired StudentProfileRepository students; @Autowired ProfessorRepository professors;
 @Autowired jakarta.persistence.EntityManager em;
 @Test void eachRoleUpdatesOnlyNameAndPreservesAcademicAndSecurityFields() throws Exception {
  for(Role role:Role.values()) {
   var u=new UserEntity();u.setName("Before");u.setEmail(role+"@example.test");u.setRole(role);u.setStatus(Status.ACTIVE);u.setPassword("unchanged-hash");u.setTokenVersion(5);u=users.saveAndFlush(u);
   if(role==Role.STUDENT){var p=new StudentProfileEntity();p.setUser(u);p.setStudentId("S123");p.setSection("A");p.setYearLevel(YearLevel.FIRST_YEAR);students.saveAndFlush(p);}
   if(role==Role.PROFESSOR){var p=new ProfessorEntity();p.setUser(u);p.setProfessorId("P123");professors.saveAndFlush(p);}
   var auth=new UsernamePasswordAuthenticationToken(u.getEmail(),null,List.of(new SimpleGrantedAuthority("ROLE_"+role.name())));
   var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new com.ptc.halo.controller.SelfProfileController(service)).build();
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/profile").principal(auth)
     .contentType("application/json").content("{\"fullName\":\"  Talidong, Archie V.  \"}"))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.name").value("Talidong, Archie V."));
   var result=service.updateName(auth,"  Talidong, Archie V.  ");assertEquals("Talidong, Archie V.",result.name());
   em.flush();em.clear();var saved=users.findById(u.getId()).orElseThrow();
   assertEquals(result.name(),saved.getName());assertEquals(u.getEmail(),saved.getEmail());assertEquals(role,saved.getRole());assertEquals(Status.ACTIVE,saved.getStatus());assertEquals(5,saved.getTokenVersion());assertEquals("unchanged-hash",saved.getPassword());
   if(role==Role.STUDENT){var p=students.findByUserId(u.getId()).orElseThrow();assertEquals("S123",p.getStudentId());assertEquals("A",p.getSection());assertEquals(YearLevel.FIRST_YEAR,p.getYearLevel());}
   if(role==Role.PROFESSOR)assertEquals("P123",professors.findByUserId(u.getId()).orElseThrow().getProfessorId());
   assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.updateName(auth,"   "));
   assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.updateName(auth,"X".repeat(101)));
   assertEquals(result.name(),users.findById(u.getId()).orElseThrow().getName());
  }
  assertEquals(Role.values().length,logs.count());assertTrue(logs.findAll().stream().allMatch(l->l.getAction().equals("Updated their profile name")));
 }
}
