package com.ptc.halo.entity;

import com.ptc.halo.enums.Role;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "admin_acting_session", indexes = @Index(name = "idx_acting_admin_expiry", columnList = "admin_id,expires_at"))
public class AdminActingSessionEntity {
    @Id @Column(length = 36, updatable = false) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false, updatable = false) private UserEntity admin;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false, updatable = false) private UserEntity target;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, length = 32) private Role targetRole;
    @Column(nullable = false, updatable = false) private long adminTokenVersion;
    @Column(nullable = false, updatable = false) private long targetTokenVersion;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false, updatable = false) private Instant expiresAt;
    private Instant revokedAt;
    protected AdminActingSessionEntity() {}
    public AdminActingSessionEntity(String id, UserEntity admin, UserEntity target, Instant createdAt, Instant expiresAt) {
        this.id = id; this.admin = admin; this.target = target; this.targetRole = target.getRole();
        this.adminTokenVersion = admin.getTokenVersion(); this.targetTokenVersion = target.getTokenVersion();
        this.createdAt = createdAt; this.expiresAt = expiresAt;
    }
    public String getId() { return id; }
    public UserEntity getAdmin() { return admin; }
    public UserEntity getTarget() { return target; }
    public Role getTargetRole() { return targetRole; }
    public long getAdminTokenVersion() { return adminTokenVersion; }
    public long getTargetTokenVersion() { return targetTokenVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }
}
