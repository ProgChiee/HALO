package com.ptc.halo;

import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.AssessmentAttemptRepository;
import com.ptc.halo.service.StudentDashboardService;
import com.ptc.halo.service.StudentStudyActivityService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties="spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", showSql=false)
@Import(StudentDashboardService.class)
class StudentDashboardLatestAttemptTest {
    @Autowired TestEntityManager em;
    @Autowired AssessmentAttemptRepository attempts;
    @Autowired StudentDashboardService dashboard;
    @MockBean StudentStudyActivityService activity;
    UserEntity student;
    AssessmentEntity assessment;
    final LocalDateTime time = LocalDateTime.of(2026, 10, 26, 12, 0);

    @BeforeEach void setup() {
        student = student("first@test.example");
        var subject = new SubjectEntity();
        subject.setSubjectCode("TIE"); subject.setSubjectName("Test subject"); subject.setYearLevel(YearLevel.FIRST_YEAR);
        em.persist(subject);
        var week = new WeekEntity(); week.setSubject(subject); week.setWeekNumber(1); week.setTitle("Week"); em.persist(week);
        var module = new AiLearningModuleEntity(); module.setWeek(week); module.setStatus(LessonStatus.APPROVED);
        module.setAiGenerationStatus(AiGenerationStatus.COMPLETED); em.persist(module);
        assessment = new AssessmentEntity(); assessment.setModule(module); assessment.setTitle("Quiz");
        assessment.setPassingScore(70); assessment.setStatus(AssessmentStatus.AVAILABLE); assessment.setCreatedAt(time); em.persist(assessment);
    }

    UserEntity student(String email) {
        var user = new UserEntity(); user.setEmail(email); user.setName("Synthetic Student");
        user.setRole(Role.STUDENT); user.setStatus(Status.ACTIVE); return em.persist(user);
    }

    AssessmentAttemptEntity attempt(UserEntity owner, LocalDateTime submitted, int score) {
        var a = new AssessmentAttemptEntity(); a.setStudent(owner); a.setAssessment(assessment);
        a.setStartedAt(time.minusDays(2)); a.setSubmittedAt(submitted); a.setScore(score);
        a.setPassed(submitted != null && score >= 70); return em.persistAndFlush(a);
    }

    @Test void higherIdWinsIdenticalSubmissionTimesWithoutChangingScoresOrPassedCount() {
        var first = attempt(student, time, 80);
        var second = attempt(student, time, 40);
        assertTrue(second.getId() > first.getId());
        assertEquals(second.getId(), attempts.findTopByStudentIdAndSubmittedAtIsNotNullOrderBySubmittedAtDescIdDesc(student.getId()).orElseThrow().getId());
        assertEquals(40, dashboard.getDashboard(student).getLatestAssessmentScore());
        assertEquals(1L, dashboard.getDashboard(student).getPassedAssessments());
        em.clear();
        assertEquals(80, attempts.findById(first.getId()).orElseThrow().getScore());
        assertEquals(40, attempts.findById(second.getId()).orElseThrow().getScore());
    }

    @Test void submissionTimeTakesPriorityOverHigherIdAndExcludesUnsubmittedAndOtherStudents() {
        var latest = attempt(student, time, 40);
        attempt(student, time.minusDays(1), 90);
        attempt(student, null, 0);
        attempt(student("other@test.example"), time.plusDays(1), 100);
        assertEquals(latest.getId(), attempts.findTopByStudentIdAndSubmittedAtIsNotNullOrderBySubmittedAtDescIdDesc(student.getId()).orElseThrow().getId());
        assertEquals(40, dashboard.getDashboard(student).getLatestAssessmentScore());
    }

    @Test void noSubmittedHistoryReturnsNoScore() {
        assertNull(dashboard.getDashboard(student).getLatestAssessmentScore());
        attempt(student, null, 0);
        assertNull(dashboard.getDashboard(student).getLatestAssessmentScore());
        assertEquals(0L, dashboard.getDashboard(student).getPassedAssessments());
    }
}
