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
    private final com.ptc.halo.repository.UserRepository users;

    public ActivityLogService(ActivityLogRepository activityLogRepository, com.ptc.halo.repository.UserRepository users) {
        this.activityLogRepository = activityLogRepository; this.users = users;
    }

    // AuthAuditService owns the independent, best-effort transaction.
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void createAuthLog(UserEntity user, com.ptc.halo.enums.AuthEvent event, String identifier, String key) {
        if(key!=null && activityLogRepository.existsByAuthEventKey(key)) return;
        var log=new ActivityLogEntity();log.setUser(user);log.setActivityType(ActivityType.AUTH);
        log.setAuthEvent(event);log.setAuthEventKey(key);
        log.setAction(event.name());
        log.setAuthDisplay(AuthAuditService.description(event,user,identifier));
        log.setCreatedAt(LocalDateTime.now());activityLogRepository.saveAndFlush(log);
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void createPreviewLog(UserEntity admin, UserEntity target, String action) {
        ActivityLogEntity log = new ActivityLogEntity();
        log.setUser(admin);
        log.setActingTarget(target);
        log.setActingTargetRole(target.getRole());
        log.setActivityType(ActivityType.ACCOUNT);
        log.setAction(action + " by Admin " + admin.getId() + " viewing " + (target.getRole() == Role.STUDENT ? "Student" : "Professor") + " " + target.getId());
        log.setCreatedAt(LocalDateTime.now());
        activityLogRepository.save(log);
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

    public void createStudentLog(UserEntity student, com.ptc.halo.entity.AdminActingSessionEntity acting,
            ActivityType type, String action) {
        if (acting == null) { createLog(student, type, action); return; }
        if (acting.getTargetRole() != Role.STUDENT || !acting.getTarget().getId().equals(student.getId()))
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
    public org.springframework.data.domain.Page<ActivityLogResponse> getSuperAdminLogs(int page,int size,String search,ActivityType type) {
        var logs=activityLogRepository.findSuperAdminLogs(type,search.trim().toLowerCase(java.util.Locale.ROOT),paging(page,size));
        var display=ActivityDisplay.prepare(logs.getContent(),users);
        return logs.map(log->response(log,display.apply(log)));
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ActivityLogResponse> getAllLogs() {
        var logs=activityLogRepository.findAll();var display=ActivityDisplay.prepare(logs,users);
        return logs.stream().map(log->response(log,display.apply(log))).toList();
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ActivityLogResponse> getLogs(Role role,ActivityType type,int page,int size,String search) {
        var logs=activityLogRepository.findAdminLogs(role,type,search.toLowerCase(java.util.Locale.ROOT),paging(page,size));
        var display=ActivityDisplay.prepare(logs.getContent(),users);
        return logs.map(log->response(log,display.apply(log)));
    }
    private org.springframework.data.domain.Pageable paging(int page,int size) {
        return org.springframework.data.domain.PageRequest.of(page,Math.min(size,100),org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"createdAt","id"));
    }
    private ActivityLogResponse response(ActivityLogEntity log,String display) {
        var response=new ActivityLogResponse();var user=log.getUser();
        response.setId(log.getId());response.setUserName(user==null && log.getAuthEvent()!=null ? "Unknown account" : ActivityDisplay.name(user,"Admin"));
        response.setUserEmail(user==null?null:user.getEmail());response.setUserRole(user==null?null:user.getRole());
        response.setActivityType(log.getActivityType());response.setAction(log.getAction());response.setDisplayAction(display);response.setCreatedAt(log.getCreatedAt());
        return response;
    }
}
