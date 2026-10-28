package com.ptc.halo.dtoResponse;

import com.ptc.halo.enums.BadgeType;

/** Public badge metadata; contains neither account data nor award state. */
public record BadgeDefinitionResponse(BadgeType badgeType, String badgeName, String description) {}
