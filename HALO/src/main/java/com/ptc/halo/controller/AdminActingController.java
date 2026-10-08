package com.ptc.halo.controller;

import com.ptc.halo.dtoRequest.AdminActingSessionRequest;
import com.ptc.halo.dtoResponse.*;
import com.ptc.halo.enums.Role;
import com.ptc.halo.service.AdminActingSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/acting")
@PreAuthorize("hasRole('ADMIN')")
public class AdminActingController {
    private final AdminActingSessionService service;
    public AdminActingController(AdminActingSessionService service) { this.service = service; }
    public record TargetPage(List<AdminActingTargetResponse> content, int page, int size, long totalElements, int totalPages) {}
    @GetMapping("/targets")
    public TargetPage targets(Authentication authentication, @RequestParam Role role,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(defaultValue = "") @Size(max = 100) String search) {
        var result = service.targets(authentication, role, page, size, search);
        return new TargetPage(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
    @PostMapping("/sessions")
    public ResponseEntity<AdminActingSessionResponse> create(Authentication authentication, @Valid @RequestBody AdminActingSessionRequest request) {
        return ResponseEntity.status(201).body(service.create(authentication, request));
    }
    @GetMapping("/sessions/{id}")
    public AdminActingSessionResponse validate(Authentication authentication, @PathVariable UUID id) {
        return service.validate(authentication, id.toString());
    }
    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revoke(Authentication authentication, @PathVariable UUID id) {
        service.revoke(authentication, id.toString());
        return ResponseEntity.noContent().build();
    }
}
