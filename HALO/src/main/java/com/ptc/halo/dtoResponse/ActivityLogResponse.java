package com.ptc.halo.dtoResponse;

import com.ptc.halo.enums.ActivityType;
import com.ptc.halo.enums.Role;

import java.time.LocalDateTime;

public class ActivityLogResponse {

    private String activityTitle;
    private String activityDescription;
    private String activityKind;
    public String getActivityTitle() { return activityTitle; }
    public void setActivityTitle(String value) { activityTitle = value; }
    public String getActivityDescription() { return activityDescription; }
    public void setActivityDescription(String value) { activityDescription = value; }
    public String getActivityKind() { return activityKind; }
    public void setActivityKind(String value) { activityKind = value; }
    private Long id;
    private String userName;
    private String userEmail;
    private Role userRole;
    private ActivityType activityType;
    private String action;
    private String displayAction;
    public String getDisplayAction() { return displayAction; }
    public void setDisplayAction(String value) { displayAction = value; }
    private LocalDateTime createdAt;

    public ActivityLogResponse() {
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


    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Role getUserRole() {
        return userRole;
    }

    public void setUserRole(Role userRole) {
        this.userRole = userRole;
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public void setActivityType(ActivityType activityType) {
        this.activityType = activityType;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}