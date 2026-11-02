package com.ptc.halo.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.ResponseEntity;

/** Readiness check for Render; deliberately does not query external dependencies. */
@RestController
public class HealthController {
    private final ApplicationAvailability availability;

    public HealthController(ApplicationAvailability availability) {
        this.availability = availability;
    }

    @GetMapping(value = "/healthz", produces = "application/json")
    public ResponseEntity<Map<String, String>> health() {
        boolean ready = availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC;
        return ResponseEntity.status(ready ? 200 : 503)
                .body(Map.of("status", ready ? "ok" : "not_ready"));
    }
}
