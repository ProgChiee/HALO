package com.ptc.halo.service;

import com.ptc.halo.dtoResponse.ActivityLogResponse;
import com.ptc.halo.entity.ActivityLogEntity;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.ActivityType;
import com.ptc.halo.enums.Role;
import com.ptc.halo.repository.ActivityLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ActivityLogService {
    private final ActivityLogRepository activityLogRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    // Must participate in the mutation transaction; the session supplies immutable provenance.
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void createActingLog(com.ptc.halo.entity.AdminActingSessionEntity session, String action) {
        createActingLog(session, ActivityType.ACCOUNT, action);
    }
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void createActingLog(com.ptc.halo.entity.AdminActingSessionEntity session, ActivityType type, String action) {
        ActivityLogEntity log = new ActivityLogEntity();
        log.setUser(session.getAdmin());
        log.setActingTarget(session.getTarget());
        log.setActingTargetRole(session.getTargetRole());
        log.setActingSession(session);
        log.setActivityType(type);
        log.setAction(action + " by Admin " + session.getAdmin().getId() + " acting as "
                + session.getTargetRole() + " " + session.getTarget().getId());
        log.setCreatedAt(LocalDateTime.now());
        activityLogRepository.save(log);
    }

    public void createProfessorLog(UserEntity professor, com.ptc.halo.entity.AdminActingSessionEntity acting,
            ActivityType type, String action) {
        if (acting == null) { createLog(professor, type, action); return; }
        if (acting.getTargetRole() != Role.PROFESSOR || !acting.getTarget().getId().equals(professor.getId()))
            throw new org.springframework.security.access.AccessDeniedException("Invalid acting target");
        createActingLog(acting, type, action);
    }

    public void createLog(
            UserEntity user,
            ActivityType activityType,
            String action) {

        ActivityLogEntity log =
                new ActivityLogEntity();

        log.setUser(user);

        log.setActivityType(
                activityType
        );

        log.setAction(
                action
        );

        log.setCreatedAt(
                LocalDateTime.now()
        );

        activityLogRepository.save(log);
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ActivityLogResponse> getSuperAdminLogs(int page, int size, String search, ActivityType type) {
        var paging = org.springframework.data.domain.PageRequest.of(page, Math.min(size, 100),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id"));
        return activityLogRepository.findSuperAdminLogs(type, search.trim().toLowerCase(java.util.Locale.ROOT), paging)
                .map(log -> {
                    ActivityLogResponse response = new ActivityLogResponse();
                    response.setId(log.getId());
                    response.setUserName(log.getUser().getName());
                    response.setUserEmail(log.getUser().getEmail());
                    response.setUserRole(log.getUser().getRole());
                    response.setActivityType(log.getActivityType());
                    response.setAction(log.getAction());
                    response.setCreatedAt(log.getCreatedAt());
                    return response;
                });
    }
    public List<ActivityLogResponse> getAllLogs() {

        return activityLogRepository.findAll()
                .stream()
                .map(log -> {

                    ActivityLogResponse response =
                            new ActivityLogResponse();

                    response.setId(
                            log.getId()
                    );

                    response.setUserName(
                            log.getUser().getName()
                    );

                    response.setUserEmail(
                            log.getUser().getEmail()
                    );

                    response.setUserRole(
                            log.getUser().getRole()
                    );

                    response.setActivityType(
                            log.getActivityType()
                    );

                    response.setAction(
                            log.getAction()
                    );

                    response.setCreatedAt(
                            log.getCreatedAt()
                    );

                    return response;
                })
                .toList();
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ActivityLogResponse> getLogs(
            Role role, ActivityType activityType, int page, int size, String search) {
        var logs = activityLogRepository.findAdminLogs(role, activityType,
                search.toLowerCase(java.util.Locale.ROOT),
                org.springframework.data.domain.PageRequest.of(page, Math.min(size, 100),
                    org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id")));
        return logs
                .map(log -> {

                    ActivityLogResponse response =
                            new ActivityLogResponse();

                    response.setId(log.getId());
                    response.setUserName(
                            log.getUser().getName()
                    );
                    response.setUserEmail(
                            log.getUser().getEmail()
                    );
                    response.setUserRole(
                            log.getUser().getRole()
                    );
                    response.setActivityType(
                            log.getActivityType()
                    );
                    response.setAction(
                            log.getAction()
                    );
                    response.setCreatedAt(
                            log.getCreatedAt()
                    );

                    return response;
                });
    }
}