package com.ptc.halo.controller;

import com.ptc.halo.dtoResponse.AdminDashboardResponse;
import com.ptc.halo.service.AdminDashboardService;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {
    private static final Logger log = LoggerFactory.getLogger(AdminDashboardController.class);

    private final AdminDashboardService
            adminDashboardService;

    public AdminDashboardController(
            AdminDashboardService adminDashboardService) {

        this.adminDashboardService =
                adminDashboardService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<AdminDashboardResponse>
    getDashboard() {

        return ResponseEntity.ok(
                adminDashboardService
                        .getDashboard()
        );
    }
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/recent-activity")
    public ResponseEntity<?>
    getRecentActivity() {
        try {
            return ResponseEntity.ok(adminDashboardService.getRecentActivity());
        } catch (AuthenticationException | AccessDeniedException securityError) {
            throw securityError;
        } catch (RuntimeException error) {
            // Resolve here so an error dispatch cannot mask this failure as a 401.
            log.error("admin_recent_activity_failed exceptionType={}", error.getClass().getName());
            return ResponseEntity.status(500).body(Map.of(
                    "status", 500,
                    "code", "ACTIVITY_LOGS_UNAVAILABLE",
                    "message", "Activity logs are temporarily unavailable."));
        }
    }
}
