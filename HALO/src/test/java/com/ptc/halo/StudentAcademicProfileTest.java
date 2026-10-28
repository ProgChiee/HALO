package com.ptc.halo;

import com.ptc.halo.dtoRequest.StudentAcademicUpdateRequest;
import com.ptc.halo.dtoRequest.StudentAcademicUpdateRequest.Field;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"}, showSql=false)
@Import({StudentAcademicProfileService.class, ProfileService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class StudentAcademicProfileTest {
    @Autowired StudentAcademicProfileService service;
    @Autowired UserRepository users;
    @SpyBean StudentProfileRepository profiles;
    @MockBean ActivityLogService activity;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    UserEntity student;
    @BeforeEach void setup() { student = account(Role.STUDENT); profile(student); }
    UserEntity account(Role role) {
        var u = new UserEntity(); u.setName("Synthetic student"); u.setEmail(UUID.randomUUID()+"@example.test");
        u.setPassword("unchanged-hash"); u.setRole(role); u.setStatus(Status.ACTIVE); u.setTokenVersion(7);
        return users.saveAndFlush(u);
    }
    StudentProfileEntity profile(UserEntity u) {
        var p = new StudentProfileEntity(); p.setUser(u); p.setStudentId("legacy-"+u.getId());
        p.setSection("BSHM 1A"); p.setYearLevel(YearLevel.FIRST_YEAR); return profiles.saveAndFlush(p);
    }
    void update(UserEntity u, Field field, String value) { service.update(TestIdentity.auth(u),new StudentAcademicUpdateRequest(field,value)); }
    @Test void individualUpdatesNormalizeAndPersistWithoutReplacingIdentityOrUnchangedLegacyFields() {
        Long profileId = profiles.findByUserId(student.getId()).orElseThrow().getId();
        update(student,Field.SECTION,"  BSHM   2B  ");
        assertEquals("legacy-"+student.getId(),profiles.findByUserId(student.getId()).orElseThrow().getStudentId());
        update(student,Field.STUDENT_ID," 29AHRT-0101 "); update(student,Field.YEAR_LEVEL,"SECOND_YEAR");
        var p = profiles.findByUserId(student.getId()).orElseThrow();
        assertEquals(profileId,p.getId()); assertEquals("29AHRT-0101",p.getStudentId());
        assertEquals("BSHM 2B",p.getSection()); assertEquals(YearLevel.SECOND_YEAR,p.getYearLevel());
        var current = users.findById(student.getId()).orElseThrow();
        assertEquals(student.getEmail(),current.getEmail()); assertEquals(student.getName(),current.getName());
        assertEquals("unchanged-hash",current.getPassword()); assertEquals(7,current.getTokenVersion());
        assertEquals(Role.STUDENT,current.getRole()); assertEquals(Status.ACTIVE,current.getStatus());
        verify(activity,times(3)).createLog(any(),eq(ActivityType.ACCOUNT),eq("Updated their academic profile"));
    }
    @Test void invalidValuesAndDuplicatesNeverChangeStoredProfile() {
        for (String id : List.of("", "2023-00123", "29OTHER-0001", "29ahrt-0001", "29AHRT0001"))
            assertThrows(RegistrationValidationException.class,()->update(student,Field.STUDENT_ID,id));
        for (String section : List.of(" ", "<script>", "X".repeat(51)))
            assertThrows(RegistrationValidationException.class,()->update(student,Field.SECTION,section));
        for (String year : List.of("", "THIRD_YEAR"))
            assertThrows(RegistrationValidationException.class,()->update(student,Field.YEAR_LEVEL,year));
        var other = account(Role.STUDENT); profile(other); update(other,Field.STUDENT_ID,"29AHRT-0102");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->update(student,Field.STUDENT_ID,"29AHRT-0102"));
        assertEquals("legacy-"+student.getId(),profiles.findByUserId(student.getId()).orElseThrow().getStudentId());
        assertEquals("BSHM 1A",profiles.findByUserId(student.getId()).orElseThrow().getSection());
    }
    @Test void missingProfilesOtherRolesAndStaleSessionsFailClosed() {
        var missing = account(Role.STUDENT);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->update(missing,Field.SECTION,"BSHM 2B"));
        assertTrue(profiles.findByUserId(missing.getId()).isEmpty());
        for (Role role : List.of(Role.ADMIN,Role.SUPER_ADMIN,Role.PROFESSOR))
            assertThrows(org.springframework.security.access.AccessDeniedException.class,()->update(account(role),Field.SECTION,"BSHM 2B"));
        var auth = TestIdentity.auth(student);
        student.setTokenVersion(8); users.saveAndFlush(student);
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,()->service.update(auth,new StudentAcademicUpdateRequest(Field.SECTION,"BSHM 2B")));
        for (Status status : List.of(Status.INACTIVE,Status.ARCHIVED,Status.BLOCKED)) {
            student.setStatus(status); users.saveAndFlush(student);
            assertThrows(org.springframework.security.access.AccessDeniedException.class,()->update(student,Field.SECTION,"BSHM 2B"));
        }
    }
    @Test void concurrentFieldEditsPreserveBothChanges() throws Exception {
        var start = new CyclicBarrier(2); var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(()->{start.await(10,TimeUnit.SECONDS);update(student,Field.SECTION,"BSHM 2C");return true;});
            var b = pool.submit(()->{start.await(10,TimeUnit.SECONDS);update(student,Field.YEAR_LEVEL,"SECOND_YEAR");return true;});
            assertTrue(a.get(20,TimeUnit.SECONDS)); assertTrue(b.get(20,TimeUnit.SECONDS));
            var p = profiles.findByUserId(student.getId()).orElseThrow();
            assertEquals("BSHM 2C",p.getSection()); assertEquals(YearLevel.SECOND_YEAR,p.getYearLevel());
        } finally { pool.shutdownNow(); }
    }
    @Test void databaseConstraintRejectsConcurrentDuplicateIdsAfterBothAvailabilityChecksPass() throws Exception {
        var other=account(Role.STUDENT);profile(other);var barrier=new CyclicBarrier(2);
        doAnswer(invocation->{barrier.await(10,TimeUnit.SECONDS);return false;}).when(profiles).existsByStudentId("29AHRT-0999");
        var pool=Executors.newFixedThreadPool(2);
        try {
            var futures=new ArrayList<Future<Boolean>>();
            for(var u:List.of(student,other)) futures.add(pool.submit(()->{
                try{update(u,Field.STUDENT_ID,"29AHRT-0999");return true;}
                catch(org.springframework.dao.DataIntegrityViolationException expected){return false;}
            }));
            int successes=0;for(var f:futures)if(f.get(20,TimeUnit.SECONDS))successes++;
            assertEquals(1,successes);
            assertEquals(1,jdbc.queryForObject("select count(*) from student_profile_entity where student_id='29AHRT-0999'",Integer.class));
        } finally { pool.shutdownNow(); }
    }
    @Test void academicHistoryAndQuizVersionAssociationsRemainByteForByteUnchanged() {
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            var subject=new SubjectEntity();subject.setSubjectCode(UUID.randomUUID().toString());subject.setSubjectName("Synthetic subject");em.persist(subject);
            var week=new WeekEntity();week.setSubject(subject);week.setWeekNumber(1);em.persist(week);
            var module=new AiLearningModuleEntity();module.setWeek(week);em.persist(module);
            var quiz=new AssessmentEntity();quiz.setModule(module);quiz.setTitle("Historical quiz");quiz.setPassingScore(75);quiz.setStatus(AssessmentStatus.RETIRED);quiz.setCreatedAt(LocalDateTime.of(2026,1,1,12,0));quiz.setQuizVersion(2);em.persist(quiz);
            var attempt=new AssessmentAttemptEntity();attempt.setAssessment(quiz);attempt.setStudent(student);attempt.setStartedAt(quiz.getCreatedAt());attempt.setSubmittedAt(quiz.getCreatedAt());attempt.setScore(80);attempt.setPassed(true);em.persist(attempt);
            var progress=new StudentModuleProgressEntity();progress.setStudent(student);progress.setModule(module);progress.setCompleted(true);progress.setCompletedAt(quiz.getCreatedAt());em.persist(progress);
            var session=new MentorSessionEntity();session.setStudent(student);session.setModule(module);session.setStartedAt(quiz.getCreatedAt());em.persist(session);
            var message=new MentorMessageEntity();message.setSession(session);message.setSender(MessageSender.STUDENT);message.setMessage("Preserved conversation");message.setCreatedAt(quiz.getCreatedAt());em.persist(message);
            var badge=new StudentBadgeEntity();badge.setStudent(student);badge.setBadgeType(BadgeType.values()[0]);badge.setBadgeName("Historical badge");badge.setDescription("Preserved badge description");badge.setEarnedAt(quiz.getCreatedAt());em.persist(badge);
        });
        var before=snapshot();
        update(student,Field.STUDENT_ID,"29AHRT-0103");update(student,Field.SECTION,"BSHM 2C");update(student,Field.YEAR_LEVEL,"SECOND_YEAR");
        assertEquals(before,snapshot());
    }
    List<?> snapshot() {
        return List.of(jdbc.queryForList("select * from assessment_attempts where student_id=?",student.getId()),
            jdbc.queryForList("select * from student_module_progress where student_id=?",student.getId()),
            jdbc.queryForList("select * from mentor_sessions where student_id=?",student.getId()),
            jdbc.queryForList("select m.* from mentor_messages m join mentor_sessions s on s.id=m.session_id where s.student_id=?",student.getId()),
            jdbc.queryForList("select * from student_badges where student_id=?",student.getId()),
            jdbc.queryForList("select q.* from assessments q join assessment_attempts a on a.assessment_id=q.id where a.student_id=?",student.getId()));
    }
}
