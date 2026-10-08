package com.ptc.halo.dtoResponse;
import java.time.Instant;
public record AdminActingSessionResponse(String id, Long adminUserId, AdminActingTargetResponse target,
        Instant createdAt, Instant expiresAt) {}
