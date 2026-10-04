package com.ptc.halo;

import com.ptc.halo.dtoRequest.*;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.open-in-view=false"}, showSql=false)
@Import(ProfessorAcademicService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProfessorAcademicOwnershipTest {
    @Autowired SubjectRepository subjects;
    @Autowired WeekRepository weeks;
    @Autowired ProfessorRepository professors;
    @Autowired UserRepository users;
    @Autowired ProfessorAcademicService service;
    @MockBean ActivityLogService audit;
    UserEntity a, b;
    Long subjectId;
    @BeforeEach void setup() {
        weeks.deleteAll(); subjects.deleteAll(); professors.deleteAll(); users.deleteAll();
        a = professor("a"); b = professor("b");
        var request = new SubjectRequest(); request.setSubjectCode("S1"); request.setSubjectName("Subject"); request.setYearLevel(YearLevel.FIRST_YEAR);
        subjectId = service.createSubject(request, a).getId();
        clearInvocations(audit);
    }
    UserEntity professor(String name) {
        var user = new UserEntity(); user.setName(name); user.setEmail(name + "@example.test"); user.setRole(Role.PROFESSOR); user.setStatus(Status.ACTIVE);
        user = users.saveAndFlush(user);
        var professor = new ProfessorEntity(); professor.setUser(user); professor.setProfessorId(name); professors.saveAndFlush(professor);
        return user;
    }
    void unavailable(Runnable request) {
        var error = assertThrows(ResponseStatusException.class, request::run);
        assertEquals(404, error.getStatusCode().value()); assertEquals("RESOURCE_NOT_FOUND", error.getReason());
    }
    WeekRequest week() { var r = new WeekRequest(); r.setWeekNumber(1); r.setTitle("Week"); return r; }
    @Test void creationPersistsOwnerAndListsAreScoped() {
        assertEquals(professors.findByUserId(a.getId()).orElseThrow().getId(), subjects.findById(subjectId).orElseThrow().getProfessor().getId());
        assertEquals(1, service.viewAllSubjects(a).size()); assertTrue(service.viewAllSubjects(b).isEmpty());
        assertEquals(subjectId, service.viewSubjectById(subjectId, a).getId());
        unavailable(() -> service.viewSubjectById(subjectId, b));
        unavailable(() -> service.viewSubjectById(Long.MAX_VALUE, a));
    }
    @Test void foreignSubjectMutationsAndWeekListingAreRejectedBeforeAudit() {
        var update = new SubjectUpdateRequest(); update.setSubjectCode("CHANGED"); update.setSubjectName("Changed");
        unavailable(() -> service.updateSubject(subjectId, update, b));
        unavailable(() -> service.deleteSubject(subjectId, b));
        unavailable(() -> service.createWeek(subjectId, week(), b));
        unavailable(() -> service.viewAllWeeks(subjectId, b));
        verifyNoInteractions(audit);
        assertEquals("S1", subjects.findById(subjectId).orElseThrow().getSubjectCode());
        assertEquals(0, weeks.count());
    }
    @Test void ownerCanUpdateSubjectAndManageWeeksButOtherProfessorCannot() {
        var update = new SubjectUpdateRequest(); update.setSubjectCode("S2"); update.setSubjectName("Updated"); update.setYearLevel(YearLevel.FIRST_YEAR);
        service.updateSubject(subjectId, update, a);
        assertEquals("Updated", service.viewSubjectById(subjectId, a).getSubjectName());
        Long id = service.createWeek(subjectId, week(), a).getId();
        assertEquals(1, service.viewAllWeeks(subjectId, a).size());
        assertEquals(id, service.viewWeekById(id, a).getId());
        var edit = new WeekUpdateRequest(); edit.setWeekNumber(2); edit.setTitle("Updated week");
        clearInvocations(audit);
        unavailable(() -> service.viewWeekById(id, b));
        unavailable(() -> service.updateWeek(id, edit, b));
        unavailable(() -> service.deleteWeek(id, b));
        verifyNoInteractions(audit);
        service.updateWeek(id, edit, a); assertEquals(2, service.viewWeekById(id, a).getWeekNumber());
        service.deleteWeek(id, a); assertFalse(weeks.existsById(id));
        service.deleteSubject(subjectId, a); assertFalse(subjects.existsById(subjectId));
        verify(audit, times(3)).createLog(eq(a), eq(ActivityType.MODULE), anyString());
    }
    @Test void legacyUnassignedSubjectsAndWeeksRemainStoredButInaccessible() {
        var legacy = new SubjectEntity(); legacy.setSubjectCode("LEGACY"); legacy.setSubjectName("Legacy"); legacy = subjects.saveAndFlush(legacy);
        Long id = legacy.getId();
        var week = new WeekEntity(); week.setSubject(legacy); week.setWeekNumber(1); week.setTitle("Legacy week"); Long weekId=weeks.saveAndFlush(week).getId();
        unavailable(() -> service.viewSubjectById(id, a)); unavailable(() -> service.viewAllWeeks(id, a));
        unavailable(() -> service.viewWeekById(weekId, a)); unavailable(() -> service.deleteSubject(id, b));
        assertEquals(1, service.viewAllSubjects(a).size()); assertTrue(service.viewAllSubjects(b).isEmpty());
        assertTrue(subjects.existsById(id)); assertTrue(weeks.existsById(weekId)); verifyNoInteractions(audit);
    }
}
