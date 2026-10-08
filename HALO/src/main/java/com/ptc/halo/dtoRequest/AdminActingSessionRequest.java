package com.ptc.halo.dtoRequest;

import com.ptc.halo.enums.Role;
import jakarta.validation.constraints.*;

public record AdminActingSessionRequest(@NotNull @Positive Long targetUserId, @NotNull Role targetRole) {}
