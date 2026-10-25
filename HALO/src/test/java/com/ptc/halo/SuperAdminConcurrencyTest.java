package com.ptc.halo;

import com.ptc.halo.dtoRequest.UpdateAdminRequest;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="}, showSql=false)
@Import({SuperAdminService.class, ActivityLogService.class, AuthService.class, com.ptc.halo.service.LoginTokenIssuer.class, JwtService.class, SuperAdminConcurrencyTest.Config.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class SuperAdminConcurrencyTest {
    @TestConfiguration static class Config {
        @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(4); }
    }
    @Autowired SuperAdminService admins;
    @Autowired AuthService auth;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;
    @SpyBean UserRepository users;
    @MockBean ProfilePhotoService photos;
    @MockBean AuthAuditService audit;
    @MockBean AuthenticationManager authentication;
    UserEntity actor, target;
    String token;
    ExecutorService executor;
    enum Change { DEACTIVATE, LOGOUT, PASSWORD }

    @BeforeEach void setup() {
        actor = account(Role.SUPER_ADMIN); target = account(Role.ADMIN);
        token = jwt.generateToken(target); executor = Executors.newFixedThreadPool(2);
    }
    @AfterEach void cleanup() { executor.shutdownNow(); reset(users); }
    UserEntity account(Role role) {
        var u=new UserEntity(); u.setName("Synthetic account"); u.setEmail(UUID.randomUUID()+"@example.test");
        u.setRole(role); u.setStatus(Status.ACTIVE); u.setPassword(encoder.encode("Original-pass123"));
        return users.saveAndFlush(u);
    }
    void transaction(Runnable work) { new TransactionTemplate(manager).executeWithoutResult(tx -> work.run()); }
    void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS), "Timed out waiting for competing transaction"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    void edit() {
        var request=new UpdateAdminRequest(); request.setName("Updated name"); request.setEmail(target.getEmail());
        admins.updateAdmin(target.getId(), request, actor);
    }
    void changePassword(UserEntity user) {
        var service=new PasswordResetService(mock(PasswordResetOtpRepository.class), users, mock(EmailService.class),
            encoder, audit, mock(PasswordResetProtection.class), mock(PasswordResetThrottle.class), manager);
        transaction(() -> service.changePassword(user, "Original-pass123", "Replacement-pass123"));
    }
    void securityChange(Change change) {
        switch(change) {
            case DEACTIVATE -> admins.changeAdminStatus(target.getId(), Status.INACTIVE, actor);
            case LOGOUT -> auth.logout(TestIdentity.auth(target), "Bearer "+token);
            case PASSWORD -> changePassword(target);
        }
    }
    void assertPreserved(Change change) {
        var saved=users.findById(target.getId()).orElseThrow();
        assertEquals("Updated name", saved.getName());
        assertEquals(target.getEmail(), saved.getEmail()); assertEquals(Role.ADMIN, saved.getRole());
        assertEquals(change==Change.DEACTIVATE ? Status.INACTIVE : Status.ACTIVE, saved.getStatus());
        assertEquals(1, saved.getTokenVersion());
        assertFalse(jwt.isCurrent(token, new CustomUserDetails(saved)));
        assertTrue(encoder.matches(change==Change.PASSWORD ? "Replacement-pass123" : "Original-pass123", saved.getPassword()));
    }

    @ParameterizedTest @EnumSource(Change.class)
    void editRefreshesStalePersistenceContextAfterCommittedSecurityChange(Change change) throws Exception {
        var loaded=new CountDownLatch(1); var changed=new CountDownLatch(1);
        var edit=executor.submit(() -> transaction(() -> {
            var stale=em.find(UserEntity.class, target.getId());
            assertEquals(Status.ACTIVE, stale.getStatus()); assertEquals(0, stale.getTokenVersion());
            loaded.countDown(); await(changed); edit();
        }));
        try { await(loaded); securityChange(change); }
        finally { changed.countDown(); }
        edit.get(15, TimeUnit.SECONDS); assertPreserved(change);
    }

    @ParameterizedTest @EnumSource(Change.class)
    void editSerializesWithSecurityChanges(Change change) throws Exception {
        var read=new CountDownLatch(1); var release=new CountDownLatch(1); var started=new CountDownLatch(1);
        doAnswer(call -> { read.countDown(); await(release); return false; })
            .when(users).existsNormalizedEmail(target.getEmail(), target.getId());
        var edit=executor.submit(this::edit);
        Future<?> security=null;
        try {
            await(read);
            security=executor.submit(() -> { started.countDown(); securityChange(change); }); await(started);
            final Future<?> waiting=security;
            assertThrows(TimeoutException.class, () -> waiting.get(250, TimeUnit.MILLISECONDS), "Security write must wait for the Admin edit's row lock");
        } finally { release.countDown(); }
        edit.get(15, TimeUnit.SECONDS); if(security!=null) security.get(15, TimeUnit.SECONDS);
        assertPreserved(change);
    }

    @Test void statusChangeCannotOverwriteCommittedLogoutFromStaleContext() throws Exception {
        var loaded=new CountDownLatch(1); var changed=new CountDownLatch(1);
        var status=executor.submit(() -> transaction(() -> {
            em.find(UserEntity.class, target.getId()); loaded.countDown(); await(changed);
            admins.changeAdminStatus(target.getId(), Status.INACTIVE, actor);
        }));
        try { await(loaded); securityChange(Change.LOGOUT); } finally { changed.countDown(); }
        status.get(15, TimeUnit.SECONDS);
        var saved=users.findById(target.getId()).orElseThrow();
        assertEquals(Status.INACTIVE,saved.getStatus()); assertEquals(2,saved.getTokenVersion());
        assertFalse(jwt.isCurrent(token,new CustomUserDetails(saved)));
    }

    @ParameterizedTest @EnumSource(Role.class)
    void passwordChangeStillRevokesTokensForEveryRole(Role role) {
        var user=account(role); var old=jwt.generateToken(user); changePassword(user);
        var saved=users.findById(user.getId()).orElseThrow();
        assertEquals(role,saved.getRole()); assertEquals(Status.ACTIVE,saved.getStatus());
        assertFalse(jwt.isCurrent(old,new CustomUserDetails(saved)));
        assertTrue(jwt.isCurrent(jwt.generateToken(saved),new CustomUserDetails(saved)));
        assertTrue(encoder.matches("Replacement-pass123",saved.getPassword()));
    }

    int authenticatedRequest(String bearer) throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        try {
            var request=new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/admin/dashboard");
            request.addHeader("Authorization", "Bearer "+bearer);
            var response=new org.springframework.mock.web.MockHttpServletResponse();
            new JwtAuthenticationFilter(jwt,new CustomUserDetailsService(users))
                .doFilter(request,response,new org.springframework.mock.web.MockFilterChain());
            return response.getStatus();
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    String login() {
        var provider=new org.springframework.security.authentication.dao.DaoAuthenticationProvider();
        provider.setUserDetailsService(new CustomUserDetailsService(users)); provider.setPasswordEncoder(encoder);
        doAnswer(call -> provider.authenticate(call.getArgument(0))).when(authentication).authenticate(any());
        var request=new com.ptc.halo.dtoRequest.LoginRequest();
        request.setEmail(target.getEmail()); request.setPassword("Original-pass123");
        return auth.login(request).getToken();
    }

    @Test void deactivationPermanentlyRevokesTokenAndReactivationRequiresFreshLogin() throws Exception {
        String issued=login(); assertEquals(200,authenticatedRequest(issued));
        admins.changeAdminStatus(target.getId(),Status.INACTIVE,actor);
        assertNotEquals(200,authenticatedRequest(issued));
        assertEquals("ACCOUNT_INACTIVE",assertThrows(AccountStateException.class,this::login).code);
        admins.changeAdminStatus(target.getId(),Status.ACTIVE,actor);
        assertEquals(401,authenticatedRequest(issued));
        assertEquals(2,users.findById(target.getId()).orElseThrow().getTokenVersion());
        String fresh=login(); assertEquals(target.getId(),jwt.extractUserId(fresh));
        assertEquals(200,authenticatedRequest(fresh));
    }

    @Test void unchangedStatusDoesNotRevokeSessionsAgain() {
        admins.changeAdminStatus(target.getId(),Status.ACTIVE,actor);
        assertTrue(jwt.isCurrent(token,new CustomUserDetails(users.findById(target.getId()).orElseThrow())));
        admins.changeAdminStatus(target.getId(),Status.INACTIVE,actor);
        admins.changeAdminStatus(target.getId(),Status.INACTIVE,actor);
        assertEquals(1,users.findById(target.getId()).orElseThrow().getTokenVersion());
    }

    @Test void concurrentDuplicateDeactivationRevokesOnceWithoutLostUpdates() throws Exception {
        var start=new CountDownLatch(1);
        Runnable deactivate=() -> { await(start); admins.changeAdminStatus(target.getId(),Status.INACTIVE,actor); };
        var first=executor.submit(deactivate); var second=executor.submit(deactivate); start.countDown();
        first.get(15,TimeUnit.SECONDS); second.get(15,TimeUnit.SECONDS);
        var saved=users.findById(target.getId()).orElseThrow();
        assertEquals(Status.INACTIVE,saved.getStatus()); assertEquals(1,saved.getTokenVersion());
        assertFalse(jwt.isCurrent(token,new CustomUserDetails(saved)));
    }
}
