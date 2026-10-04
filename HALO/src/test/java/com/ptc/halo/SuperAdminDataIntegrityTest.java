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
        repository.deleteAll(); users.deleteAll();
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
        logs.createLog(actor,ActivityType.AUTH,"excluded super auth");
        jdbc.update("UPDATE activity_log_entity SET created_at = ?", java.sql.Timestamp.valueOf("2026-01-01 00:00:00"));
        var first = logs.getSuperAdminLogs(0,10,"",null);
        var second = logs.getSuperAdminLogs(1,10,"",null);
        assertEquals(25,first.getTotalElements()); assertEquals(3,first.getTotalPages());
        assertEquals(10,first.getNumberOfElements());
        assertTrue(first.getContent().get(9).getId() > second.getContent().get(0).getId());
        assertEquals(1,logs.getSuperAdminLogs(0,20,"event 24",ActivityType.ACCOUNT).getTotalElements());
        assertEquals(0,logs.getSuperAdminLogs(0,20,"",ActivityType.AUTH).getTotalElements());
        assertEquals(100,logs.getSuperAdminLogs(0,1000,"",null).getSize());
        assertNotNull(first.getContent().get(0).getUserName());
        assertEquals(12,admins.getAdminMonitoring().get(0).getAccountActivities());
    }
    @Test void updateCannotTakeAnotherNormalizedEmail() {
        UserEntity a=user("a@example.test",Role.ADMIN); user("b@example.test",Role.ADMIN);
        UpdateAdminRequest r=new UpdateAdminRequest(); r.setName("Admin"); r.setEmail(" B@EXAMPLE.TEST ");
        assertEquals(409,assertThrows(SuperAdminApiException.class,()->admins.updateAdmin(a.getId(),r,actor)).getStatus());
        assertEquals(404,assertThrows(SuperAdminApiException.class,()->admins.viewAdminById(actor.getId())).getStatus());
    }
}
