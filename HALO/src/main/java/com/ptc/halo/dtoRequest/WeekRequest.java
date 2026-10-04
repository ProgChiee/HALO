package com.ptc.halo.dtoRequest;

import jakarta.validation.constraints.*;

public class WeekRequest {

    @NotNull
    @Positive
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = WeekNumberDeserializer.class)
    private Integer weekNumber;

    @NotBlank
    @Size(max = 255)
    private String title;


    public WeekRequest() {
    }


    public Integer getWeekNumber() {
        return weekNumber;
    }


    public void setWeekNumber(Integer weekNumber) {
        this.weekNumber = weekNumber;
    }


    public String getTitle() {
        return title;
    }


    public void setTitle(String title) {
        this.title = title == null ? null : title.strip();
    }
}