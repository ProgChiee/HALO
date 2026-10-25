package com.ptc.halo;

import com.ptc.halo.dtoRequest.*;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.show-sql=false"}, showSql=false)
@Import({SuperAdminService.class, ActivityLogService.class, SuperAdminDataIntegrityTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SuperAdminDataIntegrityTest {
    @org.springframework.boot.test.mock.mockito.MockBean ProfilePhotoService photos;
    @TestConfiguration static class Config {
        @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(4); }
    }
    @Autowired UserRepository users;
    @Autowired ActivityLogRepository repository;
    @Autowired SuperAdminService admins;
    @SpyBean ActivityLogService logs;
    @Autowired JdbcTemplate jdbc;
    UserEntity actor;

    @BeforeEach void prepare() {
        reset(logs);
        repository.deleteAll(); jdbc.update("delete from admin_acting_session"); jdbc.update("delete from password_reset_otps"); users.deleteAll();
        jdbc.execute("ALTER TABLE user_entity ADD COLUMN IF NOT EXISTS email_normalized VARCHAR(255) GENERATED ALWAYS AS (LOWER(TRIM(email)))");
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_user_email_normalized ON user_entity(email_normalized)");
        actor = user("super@example.test", Role.SUPER_ADMIN);
    }
    UserEntity user(String email, Role role) {
        UserEntity u = new UserEntity(); u.setEmail(email); u.setName("Test Actor");
        u.setPassword("encoded-test-fixture"); u.setRole(role); u.setStatus(Status.ACTIVE);
        return users.saveAndFlush(u);
    }
    AdminRequest create(String email) {
        AdminRequest r = new AdminRequest(); r.setName("  Test Admin  "); r.setEmail(email); r.setPassword("safe-test-password"); return r;
    }
    @Test void deleteRemovesAccountAndOtpAndLoginIdentity() {
        var target=user("remove@example.test",Role.ADMIN);
        // Synthetic verifier; deletion must clean up the current challenge schema.
        String verifier = "v1:" + "a".repeat(32) + ":" + "b".repeat(64);
        jdbc.update("insert into password_reset_otps (user_id,otp,failed_attempts,expires_at,created_at) values (?, ?, 0, CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",target.getId(),verifier);
        var student=user("session-target@example.test",Role.STUDENT);
        jdbc.update("insert into admin_acting_session (id,admin_id,target_id,target_role,admin_token_version,target_token_version,created_at,expires_at) values ('delete-test',?,?,'STUDENT',0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",target.getId(),student.getId());
        admins.deleteAdmin(target.getId(),actor);
        assertEquals(0,jdbc.queryForObject("select count(*) from admin_acting_session",Integer.class));
        assertTrue(users.findById(student.getId()).isPresent());
        assertTrue(users.findById(target.getId()).isEmpty());
        assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class, () -> new com.ptc.halo.service.CustomUserDetailsService(users).loadUserByUsername(target.getEmail()));
        assertEquals(0,jdbc.queryForObject("select count(*) from password_reset_otps",Integer.class));
        verify(photos).removeDeletedAccountPhoto(target.getId());
    }
    @Test void protectedHistoryAndWrongTargetsRemainSafe() {
        var target=user("history@example.test",Role.ADMIN);
        logs.createLog(target,ActivityType.ACCOUNT,"Historical action");
        assertEquals("ACCOUNT_HAS_PROTECTED_REFERENCES",assertThrows(SuperAdminApiException.class,()->admins.deleteAdmin(target.getId(),actor)).getCode());
        assertTrue(users.findById(target.getId()).isPresent()); assertEquals(1,repository.count());
        assertEquals("ADMIN_NOT_FOUND",assertThrows(SuperAdminApiException.class,()->admins.deleteAdmin(999999L,actor)).getCode());
        var student=user("wrong@example.test",Role.STUDENT);
        assertEquals("CANNOT_DELETE_ACCOUNT",assertThrows(SuperAdminApiException.class,()->admins.deleteAdmin(student.getId(),actor)).getCode());
        assertThrows(SuperAdminApiException.class,()->admins.deleteAdmin(actor.getId(),actor));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->admins.deleteAdmin(target.getId(),target));
    }
    @Test void deleteAuditFailureRollsBack() {
        var target=user("rollback-delete@example.test",Role.ADMIN);
        doThrow(new IllegalStateException("audit failed")).when(logs).createLog(any(),any(),anyString());
        assertThrows(IllegalStateException.class,()->admins.deleteAdmin(target.getId(),actor));
        assertTrue(users.findById(target.getId()).isPresent());
    }
    @Test void normalizedDuplicatesAndDatabaseConstraint() {
        admins.createAdmin(create("  Mixed@Example.test  "),actor);
        assertEquals("Test Admin", users.findByEmail("mixed@example.test").orElseThrow().getName());
        SuperAdminApiException e = assertThrows(SuperAdminApiException.class, () -> admins.createAdmin(create("MIXED@EXAMPLE.TEST"),actor));
        assertEquals(409,e.getStatus());
        // Bypass setter and repository precheck to exercise the database constraint directly.
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO user_entity(email,token_version,must_change_password) VALUES (?,0,false)"," MIXED@example.test "));
    }
    @Test void simultaneousDuplicateInsertsOnlyOneSucceeds() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            java.util.List<Future<Boolean>> results = new java.util.ArrayList<>();
            for(String email : java.util.List.of(" Race@example.test ","race@EXAMPLE.TEST")) results.add(pool.submit(() -> {
                ready.countDown(); start.await();
                try { jdbc.update("INSERT INTO user_entity(email,token_version,must_change_password) VALUES (?,0,false)",email); return true; }
                catch(DataIntegrityViolationException e) { return false; }
            }));
            assertTrue(ready.await(5,TimeUnit.SECONDS)); start.countDown();
            assertNotEquals(results.get(0).get(10,TimeUnit.SECONDS),results.get(1).get(10,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    @Test void auditFailureRollsBackCreateUpdateAndStatus() {
        UserEntity admin = user("admin@example.test",Role.ADMIN);
        doThrow(new IllegalStateException("audit storage unavailable")).when(logs).createLog(any(),any(),anyString());
        assertThrows(IllegalStateException.class, () -> admins.createAdmin(create("new@example.test"),actor));
        assertFalse(users.existsByEmail("new@example.test"));
        UpdateAdminRequest update = new UpdateAdminRequest(); update.setName("Changed"); update.setEmail("changed@example.test");
        assertThrows(IllegalStateException.class, () -> admins.updateAdmin(admin.getId(),update,actor));
        assertEquals("admin@example.test",users.findById(admin.getId()).orElseThrow().getEmail());
        assertThrows(IllegalStateException.class, () -> admins.changeAdminStatus(admin.getId(),Status.INACTIVE,actor));
        assertEquals(Status.ACTIVE,users.findById(admin.getId()).orElseThrow().getStatus());
        assertEquals(0,repository.count());
    }
    @Test void statusIsIdempotentAndAllSuperAdminActionsAppear() {
        admins.createAdmin(create("admin@example.test"),actor);
        Long id = users.findByEmail("admin@example.test").orElseThrow().getId();
        UpdateAdminRequest update = new UpdateAdminRequest(); update.setName("Updated Admin"); update.setEmail("admin@example.test");
        admins.updateAdmin(id,update,actor);
        for(Status status : java.util.List.of(Status.ACTIVE,Status.ACTIVE,Status.INACTIVE,Status.INACTIVE))
            assertEquals(status,admins.changeAdminStatus(id,status,actor).getStatus());
        var page = logs.getSuperAdminLogs(0,20,"",null);
        assertEquals(6,page.getTotalElements());
        assertTrue(page.stream().allMatch(l -> l.getUserRole()==Role.SUPER_ADMIN));
        assertTrue(page.stream().anyMatch(l -> l.getAction().startsWith("Created")));
        assertTrue(page.stream().anyMatch(l -> l.getAction().startsWith("Updated")));
    }
    @Test void paginationFiltersOrderingAndActorFetch() {
        UserEntity admin = user("admin@example.test",Role.ADMIN);
        UserEntity student = user("student@example.test",Role.STUDENT);
        for(int i=0;i<25;i++) logs.createLog(i%2==0 ? actor : admin,ActivityType.ACCOUNT,"Event "+i);
        logs.createLog(student,ActivityType.ACCOUNT,"excluded student");
        logs.createLog(actor,ActivityType.AUTH,"included super auth");
        jdbc.update("UPDATE activity_log_entity SET created_at = ?", java.sql.Timestamp.valueOf("2026-01-01 00:00:00"));
        var first = logs.getSuperAdminLogs(0,10,"",null);
        var second = logs.getSuperAdminLogs(1,10,"",null);
        // All includes the 25 privileged ACCOUNT events and the Super Admin AUTH event.
        assertEquals(26,first.getTotalElements()); assertEquals(3,first.getTotalPages());
        assertEquals(10,first.getNumberOfElements());
        var last = logs.getSuperAdminLogs(2,10,"",null);
        assertEquals(6,last.getNumberOfElements());
        assertTrue(last.isLast());
        var all = java.util.stream.Stream.of(first,second,last).flatMap(page -> page.getContent().stream()).toList();
        assertEquals(26,all.stream().map(item -> item.getId()).distinct().count());
        assertTrue(all.stream().noneMatch(item -> item.getUserRole()==Role.STUDENT));
        assertTrue(first.getContent().get(9).getId() > second.getContent().get(0).getId());
        assertTrue(second.getContent().get(9).getId() > last.getContent().get(0).getId());
        assertEquals(1,logs.getSuperAdminLogs(0,20,"event 24",ActivityType.ACCOUNT).getTotalElements());
        var accounts = logs.getSuperAdminLogs(0,100,"",ActivityType.ACCOUNT);
        assertEquals(25,accounts.getTotalElements());
        assertTrue(accounts.stream().allMatch(item -> item.getActivityType()==ActivityType.ACCOUNT));
        var auth = logs.getSuperAdminLogs(0,20,"",ActivityType.AUTH);
        assertEquals(1,auth.getTotalElements());
        assertEquals(ActivityType.AUTH,auth.getContent().get(0).getActivityType());
        assertEquals(Role.SUPER_ADMIN,auth.getContent().get(0).getUserRole());
        assertEquals("included super auth",auth.getContent().get(0).getAction());
        assertEquals(auth.getContent().get(0).getId(),first.getContent().get(0).getId());
        assertEquals(100,logs.getSuperAdminLogs(0,1000,"",null).getSize());
        assertNotNull(first.getContent().get(0).getUserName());
        assertEquals(12,admins.getAdminMonitoring().get(0).getAccountActivities());
    }
    @Test void targetSearchPreservesVisibilityFiltersAndStablePagination() {
        var admin=user("search-actor@example.test",Role.ADMIN);
        var target=user("target.professor@example.test",Role.PROFESSOR);
        target.setName("Unique Target Name"); target=users.saveAndFlush(target);
        var student=user("search-student@example.test",Role.STUDENT);
        java.util.List<Long> expected=new java.util.ArrayList<>();
        for(int i=0;i<5;i++) {
            var log=new ActivityLogEntity(); log.setUser(admin); log.setActingTarget(target);
            log.setActingTargetRole(Role.PROFESSOR); log.setActivityType(ActivityType.ACCOUNT);
            log.setAction("Started Professor preview by Admin "+admin.getId()+" viewing Professor "+target.getId());
            log.setCreatedAt(java.time.LocalDateTime.of(2026,1,1,0,0));
            expected.add(repository.saveAndFlush(log).getId());
        }
        // Matching target data must never bypass the existing actor/type visibility clause.
        for(var owner:java.util.List.of(student,target,actor)) {
            var hidden=new ActivityLogEntity(); hidden.setUser(owner); hidden.setActingTarget(target);
            hidden.setActivityType(ActivityType.MODULE); hidden.setAction("Hidden event");
            hidden.setCreatedAt(java.time.LocalDateTime.of(2026,1,2,0,0)); repository.saveAndFlush(hidden);
        }
        java.util.Collections.reverse(expected);
        var first=logs.getSuperAdminLogs(0,2,"  UNIQUE TARGET  ",ActivityType.ACCOUNT);
        assertEquals(5,first.getTotalElements()); assertEquals(3,first.getTotalPages());
        var actual=new java.util.ArrayList<Long>();
        for(int page=0;page<3;page++) {
            var result=logs.getSuperAdminLogs(page,2,"unique target",null);
            assertEquals(5,result.getTotalElements());
            result.forEach(item->{assertEquals(Role.ADMIN,item.getUserRole()); actual.add(item.getId());
                assertTrue(item.getDisplayAction().contains("Unique Target Name"));});
        }
        assertEquals(expected,actual);
        assertEquals(expected,logs.getSuperAdminLogs(0,20,"TARGET.PROFESSOR@EXAMPLE.TEST",null).map(item->item.getId()).getContent());
        assertEquals(0,logs.getSuperAdminLogs(0,20,"unique target",ActivityType.AUTH).getTotalElements());
        assertEquals(0,logs.getSuperAdminLogs(0,20,"unique target",ActivityType.MODULE).getTotalElements());
        assertEquals(0,logs.getSuperAdminLogs(0,20,"absent target",null).getTotalElements());
    }

    @Test void updateCannotTakeAnotherNormalizedEmail() {
        UserEntity a=user("a@example.test",Role.ADMIN); user("b@example.test",Role.ADMIN);
        UpdateAdminRequest r=new UpdateAdminRequest(); r.setName("Admin"); r.setEmail(" B@EXAMPLE.TEST ");
        assertEquals(409,assertThrows(SuperAdminApiException.class,()->admins.updateAdmin(a.getId(),r,actor)).getStatus());
        assertEquals(404,assertThrows(SuperAdminApiException.class,()->admins.viewAdminById(actor.getId())).getStatus());
    }
    @Test void emailReuseDoesNotTransferJwtToNewAdmin() {
        var original=user("original@example.test",Role.ADMIN);
        var jwt=new com.ptc.halo.security.JwtService(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        String token=jwt.generateToken(original);
        var update=new UpdateAdminRequest();update.setName("Original Admin");update.setEmail("changed@example.test");
        admins.updateAdmin(original.getId(),update,actor);
        admins.createAdmin(create("original@example.test"),actor);
        var replacement=users.findByEmail("original@example.test").orElseThrow();
        assertNotEquals(original.getId(),replacement.getId());
        assertFalse(jwt.isCurrent(token,new com.ptc.halo.security.CustomUserDetails(replacement)));
        var resolved=new CustomUserDetailsService(users).loadUserById(jwt.extractUserId(token));
        assertEquals("changed@example.test",resolved.getUsername());
        assertTrue(jwt.isCurrent(token,resolved));
    }
}
