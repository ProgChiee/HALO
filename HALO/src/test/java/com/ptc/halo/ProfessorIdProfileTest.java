package com.ptc.halo;
import com.ptc.halo.dtoRequest.ProfessorIdUpdateRequest;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties="spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",showSql=false)
@Import({ProfessorIdProfileService.class,ProfileService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ProfessorIdProfileTest {
 @Autowired ProfessorIdProfileService service;
 @Autowired UserRepository users;
 @Autowired SubjectRepository subjects;
 @SpyBean ProfessorRepository professors;
 @MockBean ActivityLogService activity;
 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 UserEntity owner;
 @BeforeEach void setup(){
  jdbc.execute("ALTER TABLE professor_entity ADD COLUMN IF NOT EXISTS professor_id_normalized VARBINARY(1020) GENERATED ALWAYS AS (CAST(TRIM(professor_id) AS VARBINARY))");
  jdbc.execute("ALTER TABLE professor_entity ADD CONSTRAINT IF NOT EXISTS uk_professor_id_normalized UNIQUE(professor_id_normalized)");
  owner=account();
 }
 UserEntity account(){var u=new UserEntity();u.setName("Synthetic");u.setEmail(UUID.randomUUID()+"@example.test");u.setPassword("unchanged-hash");u.setRole(Role.PROFESSOR);u.setStatus(Status.ACTIVE);u.setTokenVersion(7);u=users.saveAndFlush(u);var p=new ProfessorEntity();p.setUser(u);p.setProfessorId("original-"+u.getId());professors.saveAndFlush(p);return u;}
 void update(UserEntity u,String id){service.update(TestIdentity.auth(u),new ProfessorIdUpdateRequest(id));}
 @Test void persistsOnlyDisplayIdentifierAndKeepsIdentityCredentialsAndCaseSensitiveRules(){
  var before=jdbc.queryForMap("select * from user_entity where id=?",owner.getId());
  var p=professors.findByUserId(owner.getId()).orElseThrow();
  var subject=new SubjectEntity();subject.setProfessor(p);subject.setSubjectCode("scope-"+owner.getId());subject.setSubjectName("Preserved course");subject=subjects.saveAndFlush(subject);
  var subjectBefore=jdbc.queryForMap("select * from subjects where id=?",subject.getId());
  update(owner," arbitrary123 ");assertEquals("arbitrary123",professors.findByUserId(owner.getId()).orElseThrow().getProfessorId());
  assertEquals(p.getId(),professors.findByUserId(owner.getId()).orElseThrow().getId());
  assertEquals(before,jdbc.queryForMap("select * from user_entity where id=?",owner.getId()));
  assertEquals(subjectBefore,jdbc.queryForMap("select * from subjects where id=?",subject.getId()));
  var other=account();update(other,"ARBITRARY123");
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->update(other," arbitrary123 "));
 }
 @Test void invalidAndStaleAccountsCannotSave(){
  for(String id:Arrays.asList(null," ","A".repeat(256)))assertThrows(org.springframework.web.server.ResponseStatusException.class,()->update(owner,id));
  var auth=TestIdentity.auth(owner);owner.setTokenVersion(8);users.saveAndFlush(owner);
  assertThrows(org.springframework.security.authentication.BadCredentialsException.class,()->service.update(auth,new ProfessorIdUpdateRequest("NEW")));
  for(Status status:List.of(Status.INACTIVE,Status.BLOCKED,Status.ARCHIVED)){owner.setStatus(status);users.saveAndFlush(owner);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->update(owner,"NEW"));}
 }
 @Test void concurrentDuplicateRequestsCannotBothCommit() throws Exception {
  var other=account();var barrier=new CyclicBarrier(2);String id="race-"+owner.getId();
  doAnswer(call->{barrier.await(10,TimeUnit.SECONDS);return false;}).when(professors).existsNormalizedProfessorId(eq(id),anyLong());
  var pool=Executors.newFixedThreadPool(2);
  try{var results=pool.invokeAll(List.of(()->attempt(owner,id),()->attempt(other,id)));int successes=0;for(var r:results)if(Boolean.TRUE.equals(r.get()))successes++;assertEquals(1,successes);assertEquals(1,jdbc.queryForObject("select count(*) from professor_entity where professor_id=?",Integer.class,id));}finally{pool.shutdownNow();}
 }
 boolean attempt(UserEntity user,String id){try{update(user,id);return true;}catch(org.springframework.dao.DataIntegrityViolationException expected){return false;}}
}

