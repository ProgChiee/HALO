package com.ptc.halo.dtoRequest;

import com.ptc.halo.enums.YearLevel;

import jakarta.validation.constraints.*;

public class SubjectRequest {

    @NotBlank
    @Size(max = 255)
    private String subjectCode;

    @NotBlank
    @Size(max = 255)
    private String subjectName;

    @Size(max = 10000)
    private String description;

    @NotNull
    private YearLevel yearLevel;

    public SubjectRequest() {
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode == null ? null : subjectCode.strip();
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName == null ? null : subjectName.strip();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? null : description.strip();
    }

    public YearLevel getYearLevel() {
        return yearLevel;
    }

    public void setYearLevel(YearLevel yearLevel) {
        this.yearLevel = yearLevel;
    }
}