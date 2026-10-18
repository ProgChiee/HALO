package com.ptc.halo.service;

import com.ptc.halo.dtoResponse.AdminDashboardResponse;
import com.ptc.halo.dtoResponse.AdminRecentActivityResponse;
import com.ptc.halo.entity.ActivityLogEntity;
import com.ptc.halo.enums.Role;
import com.ptc.halo.enums.Status;
import com.ptc.halo.repository.ActivityLogRepository;
import com.ptc.halo.repository.AiLearningModuleRepository;
import com.ptc.halo.repository.SubjectRepository;
import com.ptc.halo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final AiLearningModuleRepository aiLearningModuleRepository;
    private final ActivityLogRepository activityLogRepository;

    public AdminDashboardService(
            UserRepository userRepository,
            SubjectRepository subjectRepository,
            AiLearningModuleRepository aiLearningModuleRepository, ActivityLogRepository activityLogRepository) {

        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.aiLearningModuleRepository =
                aiLearningModuleRepository;
        this.activityLogRepository = activityLogRepository;
    }

    public AdminDashboardResponse getDashboard() {

        long totalStudents =
                userRepository.countByRoleAndStatus(Role.STUDENT, Status.ACTIVE);

        long totalProfessors =
                userRepository.countByRole(
                        Role.PROFESSOR
                );

        long activeUsers =
                userRepository.countByStatus(
                        Status.ACTIVE
                );

        long inactiveUsers =
                userRepository.countByStatus(
                        Status.INACTIVE
                );

        long totalSubjects =
                subjectRepository.count();

        long totalModules =
                aiLearningModuleRepository.count();


        AdminDashboardResponse response =
                new AdminDashboardResponse();

        response.setTotalStudents(
                totalStudents
        );

        response.setTotalProfessors(
                totalProfessors
        );

        response.setActiveUsers(
                activeUsers
        );

        response.setInactiveUsers(
                inactiveUsers
        );

        response.setTotalSubjects(
                totalSubjects
        );

        response.setTotalModules(
                totalModules
        );

        return response;
    }
    @Transactional(readOnly = true)
    public List<AdminRecentActivityResponse>
    getRecentActivity() {

        List<ActivityLogEntity> logs =
                activityLogRepository
                        .findTop10ByOrderByCreatedAtDesc();

        List<AdminRecentActivityResponse> responses =
                new ArrayList<>();


        var display = ActivityDisplay.prepare(logs, userRepository);
        for (ActivityLogEntity log : logs) {

            AdminRecentActivityResponse response =
                    new AdminRecentActivityResponse();

            response.setId(
                    log.getId()
            );

            response.setUserName(
                    log.getUser() == null && log.getAuthEvent() != null ? "Unknown account" : ActivityDisplay.name(log.getUser(), "Admin")
            );

            response.setEmail(
                    log.getUser() == null ? null : log.getUser().getEmail()
            );

            response.setRole(
                    log.getUser() == null ? null : log.getUser().getRole()
            );

            response.setActivityType(
                    log.getActivityType()
            );

            response.setDisplayAction(display.apply(log));
            response.setAction(
                    log.getAction()
            );

            response.setCreatedAt(
                    log.getCreatedAt()
            );

            responses.add(response);
        }

        return responses;
    }
}
