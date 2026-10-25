package com.ptc.halo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Schema mapping. Atomic admission is implemented with JDBC in PasswordResetThrottle. */
@Entity
@Table(name="password_reset_rate_limits", indexes=@Index(name="idx_reset_limit_window", columnList="window_started_at"))
public class PasswordResetRateLimitEntity {
    @Id @Column(length=64) private String bucketKey;
    @Column(nullable=false) private LocalDateTime windowStartedAt;
    @Column(nullable=false) private int eventCount;
    private LocalDateTime nextAllowedAt;
}
