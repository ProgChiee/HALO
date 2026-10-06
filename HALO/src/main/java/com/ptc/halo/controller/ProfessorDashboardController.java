package com.ptc.halo.controller;

import com.ptc.halo.dtoResponse.ProfessorDashboardResponse;
import com.ptc.halo.service.ProfessorDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import com.ptc.halo.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/professor/dashboard")
public class ProfessorDashboardController {

    private final UserRepository users;

    private final ProfessorDashboardService
            professorDashboardService;


    public ProfessorDashboardController(
            ProfessorDashboardService professorDashboardService, UserRepository users) {
        this.users = users;

        this.professorDashboardService =
                professorDashboardService;
    }


    @PreAuthorize("hasRole('PROFESSOR')")
    @GetMapping
    public ResponseEntity<ProfessorDashboardResponse>
    getDashboard(Authentication authentication) {
        if (authentication == null) throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Authentication required");
        var professor = users.findByEmail(authentication.getName())
                .orElseThrow(() -> new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Authentication required"));

        return ResponseEntity.ok(
                professorDashboardService
                        .getDashboard(professor)
        );
    }
}