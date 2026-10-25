package com.ptc.halo;

import com.ptc.halo.dtoRequest.StudentUpdateRequest;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"},showSql=false)
@Import({AdminService.class,ActivityLogService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class StudentProfileRecoveryTest {
    @Autowired AdminService service;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired ActivityLogRepository logs;
    @SpyBean ActivityLogService audit;
    @MockBean PasswordEncoder passwords;
    UserEntity admin, student;
    @BeforeEach void setup() {
        reset(audit); logs.deleteAll(); profiles.deleteAll(); users.deleteAll();
        admin = user("admin",Role.ADMIN); student = user("student",Role.STUDENT);
    }
    UserEntity user(String name,Role role) {
        var user = new UserEntity(); user.setName(name); user.setEmail(name+"@example.test");
        user.setPassword("unchanged-hash"); user.setRole(role); user.setStatus(Status.ACTIVE); user.setTokenVersion(7);
        return users.saveAndFlush(user);
    }
    StudentUpdateRequest request() {
        var r = new StudentUpdateRequest(); r.setName("Do not replace identity during recovery");
        r.setStudentId(" 29AHRT-0168 "); r.setSection(" BSHM  1A "); r.setYearLevel(YearLevel.FIRST_YEAR); return r;
    }
    @Test void adminCompletesOnlyVerifiedAcademicFieldsAndAuditsRecovery() {
        var result = service.updateStudent(student.getId(),request(),admin);
        assertFalse(result.isProfileIncomplete()); assertEquals("29AHRT-0168",result.getStudentId());
        assertEquals("BSHM 1A",result.getSection());
        var current = users.findById(student.getId()).orElseThrow();
        assertEquals(student.getName(),current.getName()); assertEquals(student.getEmail(),current.getEmail());
        assertEquals(Role.STUDENT,current.getRole()); assertEquals(Status.ACTIVE,current.getStatus());
        assertEquals("unchanged-hash",current.getPassword()); assertEquals(7,current.getTokenVersion());
        assertEquals(1,profiles.count()); assertEquals(1,logs.count());
        assertTrue(logs.findAll().get(0).getAction().startsWith("Completed Student academic profile:"));
    }
    @Test void incompleteInvalidAndDuplicateAcademicValuesAreRejectedWithoutPartialWrites() {
        var r=request(); r.setYearLevel(null);
        assertThrows(RegistrationValidationException.class,()->service.updateStudent(student.getId(),r,admin));
        r.setYearLevel(YearLevel.FIRST_YEAR); r.setStudentId("invented");
        assertThrows(RegistrationValidationException.class,()->service.updateStudent(student.getId(),r,admin));
        r.setStudentId("29AHRT-0168"); r.setSection(" ");
        assertThrows(RegistrationValidationException.class,()->service.updateStudent(student.getId(),r,admin));
        assertEquals(0,profiles.count()); assertEquals(0,logs.count());
        var other=user("other",Role.STUDENT); service.updateStudent(other.getId(),request(),admin);
        assertEquals(AdminApiException.Code.STUDENT_ID_CONFLICT,assertThrows(AdminApiException.class,
                ()->service.updateStudent(student.getId(),request(),admin)).code);
        assertTrue(profiles.findByUserId(student.getId()).isEmpty()); assertEquals(1,logs.count());
    }
    @Test void auditFailureRollsBackRecovery() {
        doThrow(new IllegalStateException("audit failure")).when(audit).createLog(any(),any(),any());
        assertThrows(IllegalStateException.class,()->service.updateStudent(student.getId(),request(),admin));
        assertTrue(profiles.findByUserId(student.getId()).isEmpty()); assertEquals(0,logs.count());
    }
    @Test void wrongTargetAndUnauthorizedActorsCannotRecoverProfiles() {
        for (Role role : new Role[]{Role.STUDENT,Role.PROFESSOR,Role.SUPER_ADMIN}) {
            var actor = new UserEntity(); actor.setRole(role); actor.setStatus(Status.ACTIVE);
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    ()->service.updateStudent(student.getId(),request(),actor));
        }
        assertEquals(AdminApiException.Code.INVALID_TARGET_ROLE,assertThrows(AdminApiException.class,
                ()->service.updateStudent(admin.getId(),request(),admin)).code);
        assertEquals(0,profiles.count()); assertEquals(0,logs.count());
    }
}
