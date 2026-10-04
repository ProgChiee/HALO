package com.ptc.halo.controller;

import com.ptc.halo.dtoRequest.AdminRequest;
import com.ptc.halo.dtoRequest.UpdateAdminRequest;
import com.ptc.halo.dtoResponse.*;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.service.ActivityLogService;
import com.ptc.halo.service.ProfileService;
import com.ptc.halo.service.SuperAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.ptc.halo.dtoRequest.AdminStatusRequest;

@RestController
@RequestMapping("/api/super-admin")
public class SuperAdminController {
    private final SuperAdminService superAdminService;
    private final ActivityLogService activityLogService;
    private final UserRepository userRepository;
    private final ProfileService profileService;

    public SuperAdminController(SuperAdminService superAdminService, ActivityLogService activityLogService, UserRepository userRepository, ProfileService profileService) {
        this.superAdminService = superAdminService;
        this.activityLogService = activityLogService;
        this.userRepository = userRepository;
        this.profileService = profileService;
    }


    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/create-admin")
    public ResponseEntity<AdminResponse> createAdmin(
            @Valid @RequestBody AdminRequest adminRequest,
            Authentication authentication) {

        UserEntity superAdmin =
                getCurrentSuperAdmin(authentication);

        return ResponseEntity.ok(
                superAdminService.createAdmin(
                        adminRequest,
                        superAdmin
                )
        );
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/admins")
    public ResponseEntity<List<AdminListResponse>> viewAllAdmins() {

        return ResponseEntity.ok(
                superAdminService.viewAllAdmins()
        );

    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/admin/{id}")
    public ResponseEntity<AdminListResponse> viewAdminById(
            @PathVariable @Positive Long id){

        return ResponseEntity.ok(
                superAdminService.viewAdminById(id)
        );
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/admin/{id}")
    public ResponseEntity<AdminListResponse> updateAdmin(
            @PathVariable @Positive Long id,
            @Valid @RequestBody UpdateAdminRequest request,
            Authentication authentication) {

        UserEntity superAdmin =
                getCurrentSuperAdmin(authentication);

        return ResponseEntity.ok(
                superAdminService.updateAdmin(
                        id,
                        request,
                        superAdmin
                )
        );
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PatchMapping("/admin/{id}/status")
    public ResponseEntity<AdminListResponse> changeAdminStatus(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AdminStatusRequest request,
            Authentication authentication) {

        UserEntity superAdmin =
                getCurrentSuperAdmin(authentication);

        return ResponseEntity.ok(
                superAdminService.changeAdminStatus(
                        id, request.status(),
                        superAdmin
                )
        );
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/dashboard")
    public ResponseEntity<SuperAdminDashboardResponse> getDashboard(){

        return ResponseEntity.ok(
                superAdminService.getDashboard()
        );
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/reports/users")
    public ResponseEntity<UserReportResponse> getUserReport(){

        return ResponseEntity.ok(
                superAdminService.getUserReport()
        );
    }

    private UserEntity getCurrentSuperAdmin(
            Authentication authentication) {

        return userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Super Admin not found"
                        )
                );
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/admins/monitoring")
    public ResponseEntity<List<SuperAdminAdminMonitoringResponse>>
    getAdminMonitoring() {

        return ResponseEntity.ok(
                superAdminService.getAdminMonitoring()
        );
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/activity-logs")
    public ResponseEntity<?> getActivityLogs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "") @Size(max = 200) String search,
            @RequestParam(required = false) com.ptc.halo.enums.ActivityType activityType) {
        try {
            return ResponseEntity.ok(activityLogService.getSuperAdminLogs(page, size, search, activityType));
        } catch (org.springframework.security.core.AuthenticationException | org.springframework.security.access.AccessDeniedException denied) {
            throw denied;
        } catch (Exception error) {
            org.slf4j.LoggerFactory.getLogger(SuperAdminController.class)
                    .error("super_admin_activity_logs_failed exceptionType={}", error.getClass().getName());
            return ResponseEntity.status(500).body(java.util.Map.of(
                    "status", 500, "code", "ACTIVITY_LOGS_UNAVAILABLE",
                    "message", "Activity logs are temporarily unavailable."));
        }
    }
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(
            Authentication authentication) {

        UserEntity superAdmin =
                userRepository
                        .findByEmail(authentication.getName())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Super Admin not found"
                                )
                        );

        return ResponseEntity.ok(
                profileService.getUserProfile(superAdmin)
        );
    }


}
