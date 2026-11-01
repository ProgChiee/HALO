package com.ptc.halo.dtoResponse;

import com.ptc.halo.enums.YearLevel;

public class StudentSubjectResponse {

    private Long subjectId;
    private boolean enrolled;
    // Additive management metadata; protected learning APIs remain authoritative.
    private Boolean learningAvailable;
    private String learningUnavailableReason;
    public Boolean getLearningAvailable() { return learningAvailable; }
    public void setLearningAvailable(Boolean value) { learningAvailable = value; }
    public String getLearningUnavailableReason() { return learningUnavailableReason; }
    public void setLearningUnavailableReason(String value) { learningUnavailableReason = value; }
    public boolean isEnrolled() { return enrolled; }
    public void setEnrolled(boolean enrolled) { this.enrolled = enrolled; }

    private String subjectCode;

    private String subjectName;

    private YearLevel yearLevel;

    private Integer totalWeeks;
    private Integer eligibleModuleCount;

    public Integer getEligibleModuleCount() { return eligibleModuleCount; }
    public void setEligibleModuleCount(Integer eligibleModuleCount) { this.eligibleModuleCount = eligibleModuleCount; }

    private Integer completedWeeks;

    private Integer progressPercentage;

    // Authenticated resource; 204 means no custom cover. Clients retain their local default.
    public String getCoverUrl() { return subjectId == null ? null : "subjects/" + subjectId + "/cover"; }

    public StudentSubjectResponse() {
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public YearLevel getYearLevel() {
        return yearLevel;
    }

    public void setYearLevel(YearLevel yearLevel) {
        this.yearLevel = yearLevel;
    }

    public Integer getTotalWeeks() {
        return totalWeeks;
    }

    public void setTotalWeeks(Integer totalWeeks) {
        this.totalWeeks = totalWeeks;
    }

    public Integer getCompletedWeeks() {
        return completedWeeks;
    }

    public void setCompletedWeeks(Integer completedWeeks) {
        this.completedWeeks = completedWeeks;
    }

    public Integer getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(
            Integer progressPercentage) {

        this.progressPercentage =
                progressPercentage;
    }
}
