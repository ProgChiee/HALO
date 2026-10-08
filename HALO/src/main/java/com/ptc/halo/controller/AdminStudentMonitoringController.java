package com.ptc.halo.controller;

import com.ptc.halo.dtoResponse.AdminStudentMonitoringResponse;
import com.ptc.halo.service.AdminStudentMonitoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/students")
public class AdminStudentMonitoringController {

    private final AdminStudentMonitoringService
            adminStudentMonitoringService;


    public AdminStudentMonitoringController(
            AdminStudentMonitoringService adminStudentMonitoringService) {

        this.adminStudentMonitoringService =
                adminStudentMonitoringService;
    }


    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/monitoring")
    public ResponseEntity<?>
    getStudentMonitoring(
        @RequestParam(defaultValue="0") @jakarta.validation.constraints.Min(0) int page,
        @RequestParam(defaultValue="20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int size) {

        return ResponseEntity.ok(
                adminStudentMonitoringService
                        .getStudentMonitoring(page, size)
        );
    }
}