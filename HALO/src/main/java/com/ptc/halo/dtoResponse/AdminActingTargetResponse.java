package com.ptc.halo.dtoResponse;
import com.ptc.halo.enums.Role;
public record AdminActingTargetResponse(Long userId, String name, String email, Role role) {}
