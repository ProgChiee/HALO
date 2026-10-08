package com.ptc.halo;

import com.ptc.halo.dtoRequest.AdminActingSessionRequest;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.open-in-view=false"}, showSql=false)
@Import({AdminActingSessionService.class, ActivityLogService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AdminActingSessionTest {
    @Autowired AdminActingSessionService service;
    @Autowired UserRepository users;
    @Autowired AdminActingSessionRepository sessions;
    @Autowired ActivityLogRepository logs;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @SpyBean ActivityLogService audit;
    UserEntity admin, other, professor, student;
    ActivityLogService auditTarget() { return org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit); }
    @BeforeEach void setup() {
        reset(auditTarget()); logs.deleteAll(); sessions.deleteAll(); users.deleteAll();
        admin=user("admin",Role.ADMIN); other=user("other",Role.ADMIN);
        professor=user("professor",Role.PROFESSOR); student=user("student",Role.STUDENT);
    }
    UserEntity user(String name, Role role) {
        var u=new UserEntity(); u.setName(name); u.setEmail(name+"@example.test"); u.setRole(role); u.setStatus(Status.ACTIVE);
        return users.saveAndFlush(u);
    }
    Authentication auth(UserEntity user) {
        return new UsernamePasswordAuthenticationToken(user.getEmail(), "unused", List.of(new SimpleGrantedAuthority("ROLE_"+user.getRole())));
    }
    String create(UserEntity target) {
        return service.create(auth(admin),new AdminActingSessionRequest(target.getId(),target.getRole())).id();
    }
    void code(AdminApiException.Code expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(AdminApiException.class, action).code);
    }
    @Test void createValidateRevokeAndAuditKeepActorSeparateFromTarget() {
        for(var target:List.of(professor,student)) {
            String id=create(target);
            var response=service.validate(auth(admin),id);
            assertEquals(admin.getId(),response.adminUserId()); assertEquals(target.getId(),response.target().userId());
            assertEquals(target.getRole(),response.target().role());
            assertEquals(AdminActingSessionService.LIFETIME,java.time.Duration.between(response.createdAt(),response.expiresAt()));
            assertEquals(Role.ADMIN,users.findById(admin.getId()).orElseThrow().getRole());
            service.revoke(auth(admin),id); service.revoke(auth(admin),id);
            code(AdminApiException.Code.ACTING_SESSION_INVALID,()->service.validate(auth(admin),id));
        }
        assertEquals(4,logs.count());
        new TransactionTemplate(manager).executeWithoutResult(tx -> logs.findAll().forEach(log -> {
            assertEquals(admin.getId(),log.getUser().getId());
            assertNotEquals(admin.getId(),log.getActingTarget().getId());
            assertEquals(log.getActingTarget().getRole(),log.getActingTargetRole());
            assertNotNull(log.getActingSession()); assertTrue(log.getAction().contains("by Admin "+admin.getId()));
        }));
    }
    @Test void wrongMissingInactiveAndInvalidTargetsAreRejected() {
        code(AdminApiException.Code.INVALID_TARGET_ROLE,()->service.create(auth(admin),new AdminActingSessionRequest(student.getId(),Role.PROFESSOR)));
        code(AdminApiException.Code.INVALID_TARGET_ROLE,()->service.create(auth(admin),new AdminActingSessionRequest(other.getId(),Role.ADMIN)));
        code(AdminApiException.Code.RESOURCE_NOT_FOUND,()->service.create(auth(admin),new AdminActingSessionRequest(Long.MAX_VALUE,Role.STUDENT)));
        student.setStatus(Status.INACTIVE);users.saveAndFlush(student);
        code(AdminApiException.Code.ACTING_TARGET_INACTIVE,()->create(student));
        assertEquals(0,sessions.count());assertEquals(0,logs.count());
    }
    @Test void otherAdminCannotReadOrRevokeSession() {
        String id=create(professor);
        code(AdminApiException.Code.RESOURCE_NOT_FOUND,()->service.validate(auth(other),id));
        code(AdminApiException.Code.RESOURCE_NOT_FOUND,()->service.revoke(auth(other),id));
        assertNotNull(service.validate(auth(admin),id)); assertEquals(1,logs.count());
    }
    @Test void serviceRejectsNonAdminAndStaleAdminRole() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.create(auth(student),new AdminActingSessionRequest(professor.getId(),Role.PROFESSOR)));
        var identity=auth(admin);admin.setRole(Role.STUDENT);users.saveAndFlush(admin);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.targets(identity,Role.STUDENT,0,20,""));
    }
    @Test void expiredSessionCannotBeValidatedButCanBeRevoked() {
        var expired=sessions.saveAndFlush(new AdminActingSessionEntity(UUID.randomUUID().toString(),admin,student,Instant.now().minusSeconds(60),Instant.now().minusSeconds(1)));
        code(AdminApiException.Code.ACTING_SESSION_INVALID,()->service.validate(auth(admin),expired.getId()));
        service.revoke(auth(admin),expired.getId()); assertNotNull(sessions.findById(expired.getId()).orElseThrow().getRevokedAt());
    }
    @Test void targetAndActorVersionAndStatusChangesInvalidateSession() {
        String id=create(student);student.setTokenVersion(1);users.saveAndFlush(student);
        code(AdminApiException.Code.ACTING_SESSION_INVALID,()->service.validate(auth(admin),id));
        String id2=create(professor);admin.setTokenVersion(1);users.saveAndFlush(admin);
        code(AdminApiException.Code.ACTING_SESSION_INVALID,()->service.validate(auth(admin),id2));
        String id3=create(professor);professor.setStatus(Status.INACTIVE);users.saveAndFlush(professor);
        code(AdminApiException.Code.ACTING_SESSION_INVALID,()->service.validate(auth(admin),id3));
        professor.setStatus(Status.ACTIVE);professor.setRole(Role.STUDENT);users.saveAndFlush(professor);
        code(AdminApiException.Code.ACTING_SESSION_INVALID,()->service.validate(auth(admin),id3));
    }
    @Test void targetSearchIsBoundedAndFiltersRoleStatusBeforePagination() {
        student.setStatus(Status.INACTIVE);users.saveAndFlush(student);
        assertEquals(0,service.targets(auth(admin),Role.STUDENT,0,20,"").getTotalElements());
        assertEquals(1,service.targets(auth(admin),Role.PROFESSOR,0,1000," PROFESSOR ").getTotalElements());
        assertEquals(100,service.targets(auth(admin),Role.PROFESSOR,0,1000,"").getSize());
        assertEquals(0,service.targets(auth(admin),Role.PROFESSOR,0,20,"%").getTotalElements());
        assertTrue(service.targets(auth(admin),Role.PROFESSOR,1,1,"").isEmpty());
    }
    @Test void normalAuditStillHasNoActingProvenance() {
        audit.createLog(student,ActivityType.ACCOUNT,"Normal action");
        new TransactionTemplate(manager).executeWithoutResult(tx->{var log=logs.findAll().get(0);
            assertEquals(student.getId(),log.getUser().getId());assertNull(log.getActingTarget());
            assertNull(log.getActingTargetRole());assertNull(log.getActingSession());assertEquals("Normal action",log.getAction());});
    }
    @Test void auditFailureRollsBackCreationAndRevocation() {
        doThrow(new IllegalStateException("forced audit failure")).when(auditTarget()).createActingLog(any(),anyString());
        assertThrows(IllegalStateException.class,()->create(student));assertEquals(0,sessions.count());
        reset(auditTarget());String id=create(student);
        doThrow(new IllegalStateException("forced audit failure")).when(auditTarget()).createActingLog(any(),anyString());
        assertThrows(IllegalStateException.class,()->service.revoke(auth(admin),id));
        assertNull(sessions.findById(id).orElseThrow().getRevokedAt());assertEquals(1,logs.count());
    }
}
