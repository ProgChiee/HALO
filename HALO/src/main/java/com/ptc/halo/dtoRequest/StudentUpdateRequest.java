package com.ptc.halo.dtoRequest;
import jakarta.validation.constraints.*;

import com.ptc.halo.enums.YearLevel;

public class StudentUpdateRequest {

    @NotBlank @Size(max = 100)
    private String name;

    @NotBlank @Size(max = 255)
    private String studentId;

    @NotBlank @Size(max = 255)
    private String section;

    @NotNull
    private YearLevel yearLevel;


    public StudentUpdateRequest() {
    }


    public String getName() {
        return name;
    }


    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }


    public String getStudentId() {
        return studentId;
    }


    public void setStudentId(String studentId) {
        this.studentId = studentId == null ? null : studentId.trim();
    }


    public String getSection() {
        return section;
    }


    public void setSection(String section) {
        this.section = section == null ? null : section.trim();
    }


    public YearLevel getYearLevel() {
        return yearLevel;
    }


    public void setYearLevel(YearLevel yearLevel) {
        this.yearLevel = yearLevel;
    }
}