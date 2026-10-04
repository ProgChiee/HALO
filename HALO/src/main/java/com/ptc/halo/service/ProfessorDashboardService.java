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
            AssessmentAttemptRepository assessmentAttemptRepository) {

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
        // Account directory metric is intentionally institution-wide, not a teaching roster.

        long totalStudents =
                userRepository
                        .countByRole(Role.STUDENT);


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
                assessmentAttemptRepository
                        .countByPassedTrueAndAssessment_Module_Week_Subject_Professor_User_Id(professor.getId());


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


        return response;
    }
}