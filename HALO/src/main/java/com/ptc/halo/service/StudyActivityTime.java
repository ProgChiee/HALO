package com.ptc.halo.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Study DATE/DATETIME values are Manila wall time, not UTC instants. */
public final class StudyActivityTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Manila");
    private StudyActivityTime() {}
    public static LocalDate today(Clock clock) { return now(clock).toLocalDate(); }
    public static LocalDateTime now(Clock clock) { return LocalDateTime.ofInstant(clock.instant(), ZONE); }
    public static LocalDateTime now() { return now(Clock.systemUTC()); }
}
