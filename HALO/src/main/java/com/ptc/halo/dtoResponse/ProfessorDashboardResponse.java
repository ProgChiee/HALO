package com.ptc.halo.dtoResponse;

public class ProfessorDashboardResponse {
    public record Performance(java.time.LocalDate day,Double averageScore,Long attempts) {}
    public record Lesson(Long moduleId,Long subjectId,Long weekId,String title) {}
    public record Attention(String kind,Long count,java.util.List<Lesson> lessons) {}
    public record Activity(Long id,String description,java.time.LocalDateTime createdAt) {}
    private java.util.List<Performance> performance=java.util.List.of();
    private java.util.List<Attention> attention=java.util.List.of();
    private java.util.List<Activity> recentActivity=java.util.List.of();
    private java.time.LocalDate performanceFrom;
    private java.time.LocalDate performanceThrough;
    public java.util.List<Performance> getPerformance(){return performance;}
    public void setPerformance(java.util.List<Performance> value){performance=value;}
    public java.util.List<Attention> getAttention(){return attention;}
    public void setAttention(java.util.List<Attention> value){attention=value;}
    public java.util.List<Activity> getRecentActivity(){return recentActivity;}
    public void setRecentActivity(java.util.List<Activity> value){recentActivity=value;}
    public java.time.LocalDate getPerformanceFrom(){return performanceFrom;}
    public void setPerformanceFrom(java.time.LocalDate value){performanceFrom=value;}
    public java.time.LocalDate getPerformanceThrough(){return performanceThrough;}
    public void setPerformanceThrough(java.time.LocalDate value){performanceThrough=value;}


    private Long totalStudents;
    private Long totalSubjects;
    private Long totalModules;
    private Long approvedModules;
    private Long totalAssessments;
    private Long totalPassedAttempts;

    public ProfessorDashboardResponse() {
    }

    public Long getTotalStudents() {
        return totalStudents;
    }

    public void setTotalStudents(Long totalStudents) {
        this.totalStudents = totalStudents;
    }

    public Long getTotalSubjects() {
        return totalSubjects;
    }

    public void setTotalSubjects(Long totalSubjects) {
        this.totalSubjects = totalSubjects;
    }

    public Long getTotalModules() {
        return totalModules;
    }

    public void setTotalModules(Long totalModules) {
        this.totalModules = totalModules;
    }

    public Long getApprovedModules() {
        return approvedModules;
    }

    public void setApprovedModules(Long approvedModules) {
        this.approvedModules = approvedModules;
    }

    public Long getTotalAssessments() {
        return totalAssessments;
    }

    public void setTotalAssessments(Long totalAssessments) {
        this.totalAssessments = totalAssessments;
    }

    public Long getTotalPassedAttempts() {
        return totalPassedAttempts;
    }

    public void setTotalPassedAttempts(Long totalPassedAttempts) {
        this.totalPassedAttempts = totalPassedAttempts;
    }
}