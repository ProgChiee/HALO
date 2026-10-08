package com.ptc.halo.controller;

import com.ptc.halo.dtoResponse.AdminProfessorMonitoringResponse;
import com.ptc.halo.service.AdminProfessorMonitoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/professors")
public class AdminProfessorMonitoringController {

    private final AdminProfessorMonitoringService
            adminProfessorMonitoringService;


    public AdminProfessorMonitoringController(
            AdminProfessorMonitoringService adminProfessorMonitoringService) {

        this.adminProfessorMonitoringService =
                adminProfessorMonitoringService;
    }


    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/monitoring")
    public ResponseEntity<?>
    getProfessorMonitoring(
        @RequestParam(defaultValue="0") @jakarta.validation.constraints.Min(0) int page,
        @RequestParam(defaultValue="20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int size) {

        return ResponseEntity.ok(
                adminProfessorMonitoringService
                        .getProfessorMonitoring(page, size)
        );
    }
}