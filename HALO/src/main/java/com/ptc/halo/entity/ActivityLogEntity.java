package com.ptc.halo.entity;

import com.ptc.halo.enums.ActivityType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(indexes = @Index(name = "idx_activity_created_id", columnList = "createdAt,id"))
public class ActivityLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column
    private ActivityType activityType;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acting_target_id")
    private UserEntity actingTarget;
    @Enumerated(EnumType.STRING) @Column(length = 32)
    private com.ptc.halo.enums.Role actingTargetRole;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acting_session_id")
    private AdminActingSessionEntity actingSession;

    public UserEntity getActingTarget() { return actingTarget; }
    public void setActingTarget(UserEntity value) { actingTarget = value; }
    public com.ptc.halo.enums.Role getActingTargetRole() { return actingTargetRole; }
    public void setActingTargetRole(com.ptc.halo.enums.Role value) { actingTargetRole = value; }
    public AdminActingSessionEntity getActingSession() { return actingSession; }
    public void setActingSession(AdminActingSessionEntity value) { actingSession = value; }

    public ActivityLogEntity() {
    }


    public Long getId() {
        return id;
    }


    public void setId(Long id) {
        this.id = id;
    }


    public String getAction() {
        return action;
    }


    public void setAction(String action) {
        this.action = action;
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public void setActivityType(ActivityType activityType) {
        this.activityType = activityType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }


    public UserEntity getUser() {
        return user;
    }


    public void setUser(UserEntity user) {
        this.user = user;
    }
}