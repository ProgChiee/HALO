package com.ptc.halo;

import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.security.JwtService;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="}, showSql=false)
@Import({LoginTokenIssuer.class, JwtService.class, AuthService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class StudentLoginGreetingTest {
    @Autowired UserRepository users;
    @Autowired LoginTokenIssuer issuer;
    @Autowired AuthService auth;
    @SpyBean JwtService jwt;
    @MockBean PasswordEncoder encoder;
    @MockBean AuthenticationManager authentication;
    @MockBean AuthAuditService audit;

    UserEntity account(Boolean marker, Role role) {
        var user = new UserEntity(); user.setName("Synthetic Student"); user.setEmail(UUID.randomUUID()+"@example.test");
        user.setRole(role); user.setStatus(Status.ACTIVE); user.setPassword("verified-hash"); user.setHasSuccessfulLogin(marker);
        return users.saveAndFlush(user);
    }
    com.ptc.halo.dtoResponse.LoginResponse login(UserEntity user) {
        return issuer.issue(user.getId(), user.getPassword(), user.getTokenVersion(), Set.of("ROLE_"+user.getRole()));
    }
    Object claim(String token) {
        return io.jsonwebtoken.Jwts.parser().verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(new byte[32])).build()
            .parseSignedClaims(token).getPayload().get("firstLogin");
    }
    @Test void registrationThenFirstAndSecondLoginPreserveFirstSessionClassification() {
        when(encoder.encode(anyString())).thenReturn("verified-hash");
        var request = new com.ptc.halo.dtoRequest.StudentRequest(); request.setName("Synthetic Student");
        request.setEmail(UUID.randomUUID()+"@paterostechnologicalcollege.edu.ph"); request.setPassword("Synthetic-password");
        request.setStudentId("29AHRT-3456"); request.setSection("BSHM 1A"); request.setYearLevel(YearLevel.FIRST_YEAR);
        auth.registerStudent(request); var user = users.findByEmail(request.getEmail()).orElseThrow();
        assertEquals(false, user.getHasSuccessfulLogin());
        var first = login(user); assertEquals(true, first.getFirstLogin()); assertEquals(true, claim(first.getToken()));
        assertEquals(true, users.findById(user.getId()).orElseThrow().getHasSuccessfulLogin());
        var second = login(user); assertEquals(false, second.getFirstLogin()); assertEquals(false, claim(second.getToken()));
        assertEquals(true, claim(first.getToken()));
        assertTrue(jwt.isCurrent(first.getToken(), new com.ptc.halo.security.CustomUserDetails(users.findById(user.getId()).orElseThrow())));
    }
    @Test void legacyIsNeutralThenReturningAndOtherRolesAreNotTracked() {
        var legacy = account(null, Role.STUDENT); var first = login(legacy);
        assertNull(first.getFirstLogin()); assertNull(claim(first.getToken())); assertEquals(false, login(legacy).getFirstLogin());
        for (Role role : List.of(Role.ADMIN, Role.PROFESSOR, Role.SUPER_ADMIN)) {
            var user = account(null, role); assertNull(login(user).getFirstLogin());
            assertNull(users.findById(user.getId()).orElseThrow().getHasSuccessfulLogin());
        }
    }
    @Test void concurrentDevicesHaveExactlyOneFirstLogin() throws Exception {
        var user = account(false, Role.STUDENT); var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> work = () -> { assertTrue(start.await(10, TimeUnit.SECONDS)); return login(user).getFirstLogin(); };
            var a = pool.submit(work); var b = pool.submit(work); start.countDown();
            assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertEquals(true, users.findById(user.getId()).orElseThrow().getHasSuccessfulLogin());
        } finally { pool.shutdownNow(); }
    }
    @Test void failedVerificationAndSigningRollbackDoNotConsumeFirstLogin() {
        var user = account(false, Role.STUDENT);
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
            () -> issuer.issue(user.getId(), "wrong", 0, Set.of("ROLE_STUDENT")));
        assertEquals(false, users.findById(user.getId()).orElseThrow().getHasSuccessfulLogin());
        doThrow(new IllegalStateException("synthetic signing failure")).when(jwt).generateToken(any(UserEntity.class), eq(true));
        assertThrows(IllegalStateException.class, () -> login(user));
        assertEquals(false, users.findById(user.getId()).orElseThrow().getHasSuccessfulLogin());
    }
    @Test void migrationPreservesExistingRowsAsUnknownAndCanBeRemovedInDisposableDatabase() throws Exception {
        try (var connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:greeting-migration;MODE=MariaDB", "sa", ""); var sql = connection.createStatement()) {
            sql.execute("create table user_entity(id bigint primary key, name varchar(100))");
            sql.execute("insert into user_entity values(1,'Synthetic')");
            sql.execute(java.nio.file.Files.readString(java.nio.file.Path.of("database/V20261110_01__student_successful_login_marker.sql")));
            try (var rows = sql.executeQuery("select has_successful_login from user_entity where id=1")) { assertTrue(rows.next()); assertNull(rows.getObject(1)); }
            sql.execute("insert into user_entity(id,name) values(2,'Imported')");
            try (var rows = sql.executeQuery("select count(*) from user_entity where has_successful_login is null")) { rows.next(); assertEquals(2, rows.getInt(1)); }
            sql.execute("alter table user_entity drop column has_successful_login");
            try (var rows = sql.executeQuery("select count(*) from user_entity")) { rows.next(); assertEquals(2, rows.getInt(1)); }
        }
    }
}
