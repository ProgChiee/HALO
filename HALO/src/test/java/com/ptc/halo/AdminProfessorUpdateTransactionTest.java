package com.ptc.halo;

import com.ptc.halo.dtoRequest.ProfessorUpdateRequest;
import com.ptc.halo.dtoRequest.ProfessorRequest;
import com.ptc.halo.dtoRequest.StudentUpdateRequest;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.open-in-view=false"}, showSql=false)
@Import({AdminService.class, ActivityLogService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AdminProfessorUpdateTransactionTest {
    @Autowired AdminService service;
    @Autowired UserRepository users;
    @SpyBean ProfessorRepository professors;
    @SpyBean StudentProfileRepository students;
    @SpyBean ActivityLogService audit;
    @Autowired ActivityLogRepository logs;
    @MockBean PasswordEncoder passwords;
    UserEntity admin, account;
    Long profileId;

    @BeforeEach void setup() {
        reset(audit, professors, students);
        logs.deleteAll(); professors.deleteAll(); students.deleteAll(); users.deleteAll();
        admin = user("Admin", "admin@example.test", Role.ADMIN);
        account = user("Original", "original@example.test", Role.PROFESSOR);
        var professor = new ProfessorEntity(); professor.setUser(account); professor.setProfessorId("PROF-1");
        profileId = professors.saveAndFlush(professor).getId();
    }
    ProfessorRequest createRequest() {
        var r = new ProfessorRequest(); r.setName("New professor"); r.setEmail("new@example.test");
        r.setPassword("test-password-123"); r.setProfessorId("NEW"); return r;
    }
    @ParameterizedTest @ValueSource(strings={"original@example.test", "  ORIGINAL@EXAMPLE.TEST  "})
    void normalizedDuplicateCreateAndUpdateAreRejectedWithoutPartialChanges(String email) {
        var create = createRequest(); create.setEmail(email);
        var error = assertThrows(AdminApiException.class, () -> service.createProfessor(create, admin));
        assertEquals(AdminApiException.Code.EMAIL_ALREADY_EXISTS, error.code);
        var other = user("Other", "other@example.test", Role.PROFESSOR);
        var profile = new ProfessorEntity(); profile.setUser(other); profile.setProfessorId("OTHER"); professors.saveAndFlush(profile);
        error = assertThrows(AdminApiException.class, () -> service.updateProfessor(other.getId(), request("Changed", email, "CHANGED"), admin));
        assertEquals(AdminApiException.Code.EMAIL_ALREADY_EXISTS, error.code);
        assertEquals("Other", users.findById(other.getId()).orElseThrow().getName());
        assertEquals("other@example.test", users.findById(other.getId()).orElseThrow().getEmail());
        assertEquals("OTHER", professors.findByUserId(other.getId()).orElseThrow().getProfessorId());
        assertEquals(0, logs.count());
    }
    @Test void missingResourcesAndWrongRolesHaveTypedErrors() {
        assertEquals(AdminApiException.Code.RESOURCE_NOT_FOUND, assertThrows(AdminApiException.class, () -> service.viewProfessorById(Long.MAX_VALUE)).code);
        assertEquals(AdminApiException.Code.RESOURCE_NOT_FOUND, assertThrows(AdminApiException.class, () -> service.viewStudentById(Long.MAX_VALUE)).code);
        assertEquals(AdminApiException.Code.INVALID_TARGET_ROLE, assertThrows(AdminApiException.class, () -> service.changeProfessorStatus(admin.getId(), admin)).code);
        assertEquals(AdminApiException.Code.INVALID_TARGET_ROLE, assertThrows(AdminApiException.class, () -> service.changeStudentStatus(account.getId(), admin)).code);
        assertEquals(0, logs.count());
    }
    UserEntity student() {
        var user = user("Student", "student@example.test", Role.STUDENT);
        var profile = new StudentProfileEntity(); profile.setUser(user); profile.setStudentId("ST-1");
        profile.setSection("A"); profile.setYearLevel(YearLevel.FIRST_YEAR); students.saveAndFlush(profile); return user;
    }
    StudentUpdateRequest studentRequest() {
        var r = new StudentUpdateRequest(); r.setName("Changed student"); r.setStudentId("ST-2");
        r.setSection("B"); r.setYearLevel(YearLevel.SECOND_YEAR); return r;
    }
    @ParameterizedTest @ValueSource(strings={"create", "professorStatus", "studentUpdate", "studentStatus"})
    void auditFailureRollsBackCompleteAdminMutation(String operation) {
        var student = student();
        when(passwords.encode(anyString())).thenReturn("encoded-test-password");
        long userCount = users.count(), profileCount = professors.count();
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            call.callRealMethod(); logs.flush();
            throw new IllegalStateException("forced audit failure");
        }).when(audit).createLog(any(), any(), anyString());
        assertThrows(IllegalStateException.class, () -> {
            switch (operation) {
                case "create" -> service.createProfessor(createRequest(), admin);
                case "professorStatus" -> service.changeProfessorStatus(account.getId(), admin);
                case "studentUpdate" -> service.updateStudent(student.getId(), studentRequest(), admin);
                case "studentStatus" -> service.changeStudentStatus(student.getId(), admin);
            }
        });
        assertEquals(userCount, users.count()); assertEquals(profileCount, professors.count());
        assertFalse(users.existsByEmail("new@example.test")); assertEquals(0, logs.count());
        assertEquals(Status.INACTIVE, users.findById(account.getId()).orElseThrow().getStatus());
        var stored = users.findById(student.getId()).orElseThrow();
        assertEquals(Status.INACTIVE, stored.getStatus()); assertEquals("Student", stored.getName());
        var profile = students.findByUserId(student.getId()).orElseThrow();
        assertEquals("ST-1", profile.getStudentId()); assertEquals("A", profile.getSection()); assertEquals(YearLevel.FIRST_YEAR, profile.getYearLevel());
    }
    @Test void profileInsertFailureLeavesNoProfessorAccount() {
        when(passwords.encode(anyString())).thenReturn("encoded-test-password");
        long count = users.count();
        doThrow(new IllegalStateException("forced profile failure")).when(professors).save(any(ProfessorEntity.class));
        assertThrows(IllegalStateException.class, () -> service.createProfessor(createRequest(), admin));
        assertEquals(count, users.count()); assertFalse(users.existsByEmail("new@example.test"));
        assertEquals(1, professors.count()); assertEquals(0, logs.count());
    }
    @Test void studentProfileFailureAfterUserFlushRollsBackChanges() {
        var student = student();
        doAnswer(call -> { users.flush(); throw new IllegalStateException("forced profile update failure"); })
                .when(students).save(any(StudentProfileEntity.class));
        assertThrows(IllegalStateException.class, () -> service.updateStudent(student.getId(), studentRequest(), admin));
        assertEquals("Student", users.findById(student.getId()).orElseThrow().getName());
        var profile = students.findByUserId(student.getId()).orElseThrow();
        assertEquals("ST-1", profile.getStudentId()); assertEquals("A", profile.getSection());
        assertEquals(YearLevel.FIRST_YEAR, profile.getYearLevel()); assertEquals(0, logs.count());
    }
    @ParameterizedTest @ValueSource(strings={"professor", "student"})
    void missingProfileAfterStatusMutationRollsBackStatusAndAudit(String role) {
        var target = user("No profile", "orphan@example.test", role.equals("professor") ? Role.PROFESSOR : Role.STUDENT);
        assertThrows(RuntimeException.class, () -> {
            if (role.equals("professor")) service.changeProfessorStatus(target.getId(), admin);
            else service.changeStudentStatus(target.getId(), admin);
        });
        assertEquals(Status.INACTIVE, users.findById(target.getId()).orElseThrow().getStatus());
        assertEquals(0, logs.count());
    }
    @Test void successfulMutationsCommitTheirDataAndAudit() {
        when(passwords.encode(anyString())).thenReturn("encoded-test-password");
        var created = service.createProfessor(createRequest(), admin);
        var stored = users.findByEmail(created.getEmail()).orElseThrow();
        assertEquals("NEW", professors.findByUserId(stored.getId()).orElseThrow().getProfessorId());
        assertEquals(Status.ACTIVE, stored.getStatus()); assertEquals("encoded-test-password", stored.getPassword());
        assertEquals(Status.ACTIVE, service.changeProfessorStatus(account.getId(), admin).getStatus());
        assertEquals(Status.ACTIVE, users.findById(account.getId()).orElseThrow().getStatus());
        var student = student();
        var response = service.updateStudent(student.getId(), studentRequest(), admin);
        assertEquals(response.getName(), users.findById(student.getId()).orElseThrow().getName());
        var profile = students.findByUserId(student.getId()).orElseThrow();
        assertEquals("ST-2", profile.getStudentId()); assertEquals("B", profile.getSection()); assertEquals(YearLevel.SECOND_YEAR, profile.getYearLevel());
        assertEquals(Status.ACTIVE, service.changeStudentStatus(student.getId(), admin).getStatus());
        assertEquals(Status.ACTIVE, users.findById(student.getId()).orElseThrow().getStatus());
        assertEquals(4, logs.count());
    }
    UserEntity user(String name, String email, Role role) {
        var user = new UserEntity(); user.setName(name); user.setEmail(email); user.setRole(role);
        user.setPassword("existing-encoded-password"); user.setStatus(Status.INACTIVE);
        user.setMustChangePassword(true); user.setTokenVersion(3);
        return users.saveAndFlush(user);
    }
    ProfessorUpdateRequest request(String name, String email, String professorId) {
        var request = new ProfessorUpdateRequest(); request.setName(name); request.setEmail(email); request.setProfessorId(professorId); return request;
    }
    @ParameterizedTest @ValueSource(strings={"name", "email", "both", "unchanged"})
    void updateSurvivesReloadAndResponseMatchesDatabase(String mode) {
        String name = mode.equals("name") || mode.equals("both") ? "Changed name" : account.getName();
        String email = mode.equals("email") || mode.equals("both") ? "changed@example.test" : account.getEmail();
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        var response = service.updateProfessor(account.getId(), request(name, email, "PROF-1"), admin);
        // No test transaction: these reads use a fresh persistence context after service commit.
        var stored = users.findById(account.getId()).orElseThrow();
        var profile = professors.findByUserId(account.getId()).orElseThrow();
        assertEquals(name, stored.getName()); assertEquals(email, stored.getEmail());
        assertEquals(stored.getId(), response.getId()); assertEquals(stored.getName(), response.getName());
        assertEquals(stored.getEmail(), response.getEmail()); assertEquals(profile.getProfessorId(), response.getProfessorId());
        assertEquals(profileId, profile.getId()); assertEquals("PROF-1", profile.getProfessorId());
        assertEquals(Status.INACTIVE, stored.getStatus()); assertEquals(stored.getStatus(), response.getStatus());
        assertEquals(Role.PROFESSOR, stored.getRole()); assertEquals(account.getPassword(), stored.getPassword());
        assertEquals(3, stored.getTokenVersion()); assertTrue(stored.isMustChangePassword());
        assertEquals(1, logs.count());
        assertEquals("Updated Professor account: " + stored.getEmail(), logs.findAll().get(0).getAction());
        verifyNoInteractions(passwords);
    }
    @ParameterizedTest @ValueSource(strings={"audit", "profile"})
    void failureAfterUserFlushRollsBackUserProfileAndAudit(String stage) {
        if (stage.equals("audit")) {
            doAnswer(call -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                call.callRealMethod(); logs.flush();
                throw new IllegalStateException("forced audit failure");
            }).when(audit).createLog(any(), any(), anyString());
        } else {
            doThrow(new IllegalStateException("forced persistence failure"))
                    .when(professors).saveAndFlush(any(ProfessorEntity.class));
        }
        assertThrows(IllegalStateException.class, () -> service.updateProfessor(account.getId(), request("Changed", "changed@example.test", "PROF-2"), admin));
        var stored = users.findById(account.getId()).orElseThrow();
        assertEquals("Original", stored.getName()); assertEquals("original@example.test", stored.getEmail());
        assertEquals("PROF-1", professors.findByUserId(account.getId()).orElseThrow().getProfessorId());
        assertEquals(0, logs.count());
    }
}
