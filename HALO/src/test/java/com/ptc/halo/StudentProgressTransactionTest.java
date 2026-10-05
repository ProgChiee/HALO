package com.ptc.halo;

import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.StudentProgressService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.open-in-view=false"}, showSql = false)
@Import(StudentProgressService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentProgressTransactionTest {
    @Autowired StudentProgressService service;
    @Autowired StudentModuleProgressRepository progress;
    @Autowired UserRepository users;
    @Autowired SubjectRepository subjects;
    @Autowired WeekRepository weeks;
    @Autowired AiLearningModuleRepository modules;
    UserEntity student, other, empty;
    AiLearningModuleEntity first, second;
    Long firstWeekId, secondWeekId;
    final LocalDateTime earlier = LocalDateTime.of(2026, 1, 1, 10, 0);
    final LocalDateTime later = earlier.plusDays(1);

    @BeforeEach void setup() {
        progress.deleteAll(); modules.deleteAll(); weeks.deleteAll(); subjects.deleteAll(); users.deleteAll();
        student = student("one"); other = student("two"); empty = student("empty");
        var subject = new SubjectEntity(); subject.setSubjectCode("S"); subject.setSubjectName("Subject");
        subject = subjects.saveAndFlush(subject);
        var week = new WeekEntity(); week.setSubject(subject); week.setWeekNumber(1); week.setTitle("First");
        week = weeks.saveAndFlush(week); firstWeekId = week.getId();
        first = new AiLearningModuleEntity(); first.setWeek(week); first = modules.saveAndFlush(first);
        week = new WeekEntity(); week.setSubject(subject); week.setWeekNumber(2); week.setTitle("Second");
        week = weeks.saveAndFlush(week); secondWeekId = week.getId();
        second = new AiLearningModuleEntity(); second.setWeek(week); second = modules.saveAndFlush(second);
        record(student, first, true, earlier);
        record(student, second, false, later);
        record(other, first, true, later.plusDays(1));
    }

    UserEntity student(String name) {
        var user = new UserEntity(); user.setName(name); user.setEmail(name + "@example.test");
        user.setRole(Role.STUDENT); user.setStatus(Status.ACTIVE); return users.saveAndFlush(user);
    }

    void record(UserEntity user, AiLearningModuleEntity module, boolean completed, LocalDateTime at) {
        var row = new StudentModuleProgressEntity(); row.setStudent(user); row.setModule(module);
        row.setCompleted(completed); row.setCompletedAt(at); progress.saveAndFlush(row);
    }

    @Test void lazyModuleWeekMappingWorksWithoutCallerTransactionAndPreservesValues() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        var result = service.getProgress(student);
        assertEquals(2, result.size());
        assertEquals(second.getId(), result.get(0).getModuleId());
        assertEquals(secondWeekId, result.get(0).getWeekId());
        assertFalse(result.get(0).getCompleted()); assertEquals(later, result.get(0).getCompletedAt());
        assertEquals(first.getId(), result.get(1).getModuleId());
        assertEquals(firstWeekId, result.get(1).getWeekId());
        assertTrue(result.get(1).getCompleted()); assertEquals(earlier, result.get(1).getCompletedAt());
        assertEquals(1, result.stream().filter(row -> Boolean.TRUE.equals(row.getCompleted())).count());
    }

    @Test void eachStudentOnlySeesOwnRows() {
        var result = service.getProgress(other);
        assertEquals(1, result.size()); assertEquals(first.getId(), result.get(0).getModuleId());
        assertEquals(later.plusDays(1), result.get(0).getCompletedAt());
        assertEquals(2, service.getProgress(student).size());
    }

    @Test void studentWithoutProgressGetsEmptyList() {
        assertTrue(service.getProgress(empty).isEmpty());
    }
}
