package com.ptc.halo.service;


import com.ptc.halo.dtoRequest.AdminRequest;
import com.ptc.halo.dtoRequest.UpdateAdminRequest;
import com.ptc.halo.dtoResponse.*;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.ActivityType;
import com.ptc.halo.enums.Role;
import com.ptc.halo.enums.Status;
import com.ptc.halo.repository.ActivityLogRepository;
import com.ptc.halo.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class SuperAdminService {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    @org.springframework.beans.factory.annotation.Autowired private ProfilePhotoService photos;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;
    private final ActivityLogRepository activityLogRepository;

    public SuperAdminService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder, ActivityLogService activityLogService, ActivityLogRepository activityLogRepository
    ){
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityLogService = activityLogService;
        this.activityLogRepository = activityLogRepository;
    }


    @Transactional
    public AdminResponse createAdmin(AdminRequest request,
                                     UserEntity superAdmin){

        if(userRepository.existsNormalizedEmail(request.getEmail(), null)){
            throw SuperAdminApiException.duplicateEmail();
        }

        UserEntity admin =
                new UserEntity();

        admin.setName(request.getName());
        admin.setEmail(request.getEmail());

        admin.setPassword(
                passwordEncoder.encode(request.getPassword())
        );

        admin.setRole(Role.ADMIN);
        admin.setStatus(Status.ACTIVE);

        UserEntity savedAdmin = userRepository.saveAndFlush(admin);

        activityLogService.createLog(
                superAdmin,
                ActivityType.ACCOUNT,
                "Created Admin account ID " + savedAdmin.getId() + ": " + savedAdmin.getName()
        );

        AdminResponse response = new AdminResponse();

        response.setName(savedAdmin.getName());
        response.setEmail(savedAdmin.getEmail());
        response.setRole(savedAdmin.getRole());

        return response;
    }
    public List<AdminListResponse> viewAllAdmins() {

        List<UserEntity> admins =
                userRepository.findByRole(Role.ADMIN);

        return admins.stream()
                .map(admin -> {

                    AdminListResponse response =
                            new AdminListResponse();

                    response.setId(admin.getId());
                    response.setName(admin.getName());
                    response.setEmail(admin.getEmail());
                    response.setRole(admin.getRole());
                    response.setStatus(admin.getStatus());

                    return response;

                })
                .collect(Collectors.toList());

    }
    public AdminListResponse viewAdminById(Long id){

        UserEntity admin = userRepository.findById(id)
                .orElseThrow(() ->
                        SuperAdminApiException.notFound());

        if(admin.getRole() != Role.ADMIN){
            throw SuperAdminApiException.notFound();
        }

        AdminListResponse response = new AdminListResponse();

        response.setId(admin.getId());
        response.setName(admin.getName());
        response.setEmail(admin.getEmail());
        response.setRole(admin.getRole());
        response.setStatus(admin.getStatus());

        return response;
    }
    @Transactional
    public AdminListResponse updateAdmin(   Long id,
                                            UpdateAdminRequest request,
                                            UserEntity superAdmin){

        UserEntity admin = lockCurrentAdmin(id);

        if(admin.getRole() != Role.ADMIN){
            throw SuperAdminApiException.notFound();
        }

        if(userRepository.existsNormalizedEmail(request.getEmail(), id)){

            throw SuperAdminApiException.duplicateEmail();
        }

        admin.setName(request.getName());
        admin.setEmail(request.getEmail());

        UserEntity updatedAdmin = userRepository.saveAndFlush(admin);

        activityLogService.createLog(
                superAdmin,
                ActivityType.ACCOUNT,
                "Updated Admin account ID " + updatedAdmin.getId() + ": " + updatedAdmin.getName()
        );

        AdminListResponse response = new AdminListResponse();

        response.setId(updatedAdmin.getId());
        response.setName(updatedAdmin.getName());
        response.setEmail(updatedAdmin.getEmail());
        response.setRole(updatedAdmin.getRole());
        response.setStatus(updatedAdmin.getStatus());

        return response;
    }
    @Transactional
    public AdminListResponse changeAdminStatus(   Long id, Status status,
                                                  UserEntity superAdmin){

        UserEntity admin = lockCurrentAdmin(id);

        if(admin.getRole() != Role.ADMIN){
            throw SuperAdminApiException.notFound();
        }

        // Status transitions revoke prior sessions permanently, including after
        // reactivation. The locked, refreshed row prevents lost increments.
        if (admin.getStatus() != status) admin.setTokenVersion(Math.addExact(admin.getTokenVersion(), 1));
        admin.setStatus(status);

        UserEntity updatedAdmin = userRepository.saveAndFlush(admin);

        activityLogService.createLog(
                superAdmin,
                ActivityType.ACCOUNT,
                "Changed Admin status to "
                        + updatedAdmin.getStatus()
                        + ": "
                        + "Admin ID " + updatedAdmin.getId() + " (" + updatedAdmin.getName() + ")"
        );


        AdminListResponse response = new AdminListResponse();

        response.setId(updatedAdmin.getId());
        response.setName(updatedAdmin.getName());
        response.setEmail(updatedAdmin.getEmail());
        response.setRole(updatedAdmin.getRole());
        response.setStatus(updatedAdmin.getStatus());

        return response;
    }
    private UserEntity lockCurrentAdmin(Long id) {
        // Serialize these full-entity writes with logout and password changes on
        // the same database row. A lock alone does not refresh an entity that
        // was already loaded into this persistence context before another commit.
        UserEntity admin = entityManager.find(UserEntity.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (admin == null) throw SuperAdminApiException.notFound();
        entityManager.refresh(admin, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return admin;
    }

    @Transactional
    public void deleteAdmin(Long id, UserEntity actor) {
        if (actor == null || actor.getRole() != Role.SUPER_ADMIN)
            throw new org.springframework.security.access.AccessDeniedException("Super Admin required");
        if (id.equals(actor.getId())) throw new SuperAdminApiException(400, "CANNOT_DELETE_ACCOUNT", "You cannot delete your own account.");
        UserEntity target = entityManager.find(UserEntity.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (target == null) throw SuperAdminApiException.notFound();
        if (target.getRole() != Role.ADMIN)
            throw new SuperAdminApiException(400, "CANNOT_DELETE_ACCOUNT", "Only Admin accounts can be deleted here.");
        long history = entityManager.createQuery("select count(l) from ActivityLogEntity l left join l.actingSession session where l.user.id = :id or l.actingTarget.id = :id or session.admin.id = :id or session.target.id = :id", Long.class).setParameter("id", id).getSingleResult();
        if (history > 0) throw protectedReferences();
        entityManager.createQuery("delete from PasswordResetOtpEntity o where o.user.id = :id").setParameter("id", id).executeUpdate();
        entityManager.createQuery("delete from AdminActingSessionEntity s where s.admin.id = :id or s.target.id = :id").setParameter("id", id).executeUpdate();
        activityLogService.createLog(actor, ActivityType.ACCOUNT, "Deleted Admin account ID " + id + ": " + target.getName());
        try { userRepository.delete(target); userRepository.flush(); }
        catch (org.springframework.dao.DataIntegrityViolationException failure) { throw protectedReferences(); }
        // A failed DB transaction must never delete the account's photo.
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { photos.removeDeletedAccountPhoto(id); }
            });
    }
    private SuperAdminApiException protectedReferences() {
        return new SuperAdminApiException(409, "ACCOUNT_HAS_PROTECTED_REFERENCES", "This Admin has protected history and cannot be deleted. Deactivate the account instead.");
    }
    public SuperAdminDashboardResponse getDashboard(){

        SuperAdminDashboardResponse response = new SuperAdminDashboardResponse();

        response.setTotalUsers(userRepository.count());
        response.setTotalStudents(userRepository.countByRole(Role.STUDENT));
        response.setTotalProfessors(userRepository.countByRole(Role.PROFESSOR));
        response.setTotalAdmins(userRepository.countByRole(Role.ADMIN));
        response.setActiveUsers(userRepository.countByStatus(Status.ACTIVE));
        response.setInactiveUsers(userRepository.countByStatus(Status.INACTIVE));

        return response;
    }
    public UserReportResponse getUserReport(){

        UserReportResponse response = new UserReportResponse();

        response.setTotalUsers(userRepository.count());
        response.setTotalStudents(userRepository.countByRole(Role.STUDENT));
        response.setTotalProfessors(userRepository.countByRole(Role.PROFESSOR));
        response.setTotalAdmins(userRepository.countByRole(Role.ADMIN));
        response.setActiveUsers(userRepository.countByStatus(Status.ACTIVE));
        response.setInactiveUsers(userRepository.countByStatus(Status.INACTIVE));

        return response;
    }
    public List<SuperAdminAdminMonitoringResponse>
    getAdminMonitoring() {

        List<UserEntity> admins =
                userRepository.findByRole(Role.ADMIN);

        List<SuperAdminAdminMonitoringResponse> responses =
                new ArrayList<>();


        var activityByAdmin = activityLogRepository.summarizeAdminActivity().stream()
                .collect(Collectors.toMap(ActivityLogRepository.AdminActivitySummary::getAdminId, value -> value));
        for (UserEntity admin : admins) {

            var summary = activityByAdmin.get(admin.getId());
            long accountActivities = summary == null ? 0 : summary.getAccountActivities();
            LocalDateTime lastActivity = summary == null ? null : summary.getLastActivity();

            SuperAdminAdminMonitoringResponse response =
                    new SuperAdminAdminMonitoringResponse();


            response.setAdminId(
                    admin.getId()
            );

            response.setName(
                    admin.getName()
            );

            response.setEmail(
                    admin.getEmail()
            );

            response.setStatus(
                    admin.getStatus()
            );

            response.setAccountActivities(
                    accountActivities
            );

            response.setLastActivity(
                    lastActivity
            );


            responses.add(response);
        }


        return responses;
    }
}
