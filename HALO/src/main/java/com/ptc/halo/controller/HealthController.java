package com.ptc.halo.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight liveness check; deliberately does not query external dependencies. */
@RestController
public class HealthController {
    @GetMapping(value = "/healthz", produces = "application/json")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
