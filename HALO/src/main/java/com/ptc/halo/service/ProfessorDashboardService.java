package com.ptc.halo.service;

import com.ptc.halo.dtoResponse.ProfessorDashboardResponse;
import com.ptc.halo.enums.LessonStatus;
import com.ptc.halo.enums.Role;
import com.ptc.halo.repository.AiLearningModuleRepository;
import com.ptc.halo.repository.AssessmentAttemptRepository;
import com.ptc.halo.repository.AssessmentRepository;
import com.ptc.halo.repository.SubjectRepository;
import com.ptc.halo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ptc.halo.entity.UserEntity;
import org.springframework.security.access.AccessDeniedException;

@Service
@Transactional(readOnly = true)
public class ProfessorDashboardService {

    private final UserRepository userRepository;
    private final com.ptc.halo.repository.ProfessorDashboardRepository overview;

    private final SubjectRepository subjectRepository;

    private final AiLearningModuleRepository
            aiLearningModuleRepository;

    private final AssessmentRepository
            assessmentRepository;

    private final AssessmentAttemptRepository
            assessmentAttemptRepository;


    public ProfessorDashboardService(
            UserRepository userRepository,
            SubjectRepository subjectRepository,
            AiLearningModuleRepository aiLearningModuleRepository,
            AssessmentRepository assessmentRepository,
            AssessmentAttemptRepository assessmentAttemptRepository,com.ptc.halo.repository.ProfessorDashboardRepository overview) {
        this.overview=overview;

        this.userRepository =
                userRepository;

        this.subjectRepository =
                subjectRepository;

        this.aiLearningModuleRepository =
                aiLearningModuleRepository;

        this.assessmentRepository =
                assessmentRepository;

        this.assessmentAttemptRepository =
                assessmentAttemptRepository;
    }


    public ProfessorDashboardResponse
    getDashboard(UserEntity professor) {
        if (professor == null || professor.getId() == null || professor.getRole() != Role.PROFESSOR) {
            throw new AccessDeniedException("Professor access required");
        }
        // Current roster: ACTIVE Students eligible for an owned subject.

        long totalStudents =
                overview.students(professor.getId());


        long totalSubjects =
                subjectRepository.countByProfessor_User_Id(professor.getId());


        long totalModules =
                aiLearningModuleRepository.countByWeek_Subject_Professor_User_Id(professor.getId());


        long approvedModules =
                aiLearningModuleRepository
                        .countByWeek_Subject_Professor_User_IdAndStatus(
                                professor.getId(), LessonStatus.APPROVED
                        );


        long totalAssessments =
                assessmentRepository.countByModule_Week_Subject_Professor_User_Id(professor.getId());


        long totalPassedAttempts =
                overview.passed(professor.getId());


        ProfessorDashboardResponse response =
                new ProfessorDashboardResponse();


        response.setTotalStudents(
                totalStudents
        );

        response.setTotalSubjects(
                totalSubjects
        );

        response.setTotalModules(
                totalModules
        );

        response.setApprovedModules(
                approvedModules
        );

        response.setTotalAssessments(
                totalAssessments
        );

        response.setTotalPassedAttempts(
                totalPassedAttempts
        );


        var today=java.time.LocalDate.now();var start=today.minusDays(27);
        response.setPerformanceFrom(start);response.setPerformanceThrough(today);
        response.setPerformance(overview.performance(professor.getId(),start.atStartOfDay(),today.plusDays(1).atStartOfDay()).stream()
            .map(row->new ProfessorDashboardResponse.Performance(row.getDay(),row.getAverageScore(),row.getAttempts())).toList());
        response.setAttention(java.util.List.of("assessment","revision","approval").stream().map(kind->new ProfessorDashboardResponse.Attention(kind,
            overview.attentionCount(professor.getId(),kind),overview.attentionLessons(professor.getId(),kind,org.springframework.data.domain.PageRequest.of(0,3)).stream()
            .map(row->new ProfessorDashboardResponse.Lesson(row.getModuleId(),row.getSubjectId(),row.getWeekId(),row.getTitle())).toList())).toList());
        var recent=overview.recent(professor.getId(),org.springframework.data.domain.PageRequest.of(0,6));
        var display=ActivityDisplay.prepare(recent,userRepository);
        response.setRecentActivity(recent.stream().map(log->new ProfessorDashboardResponse.Activity(log.getId(),display.apply(log),log.getCreatedAt())).toList());
        return response;
    }
}