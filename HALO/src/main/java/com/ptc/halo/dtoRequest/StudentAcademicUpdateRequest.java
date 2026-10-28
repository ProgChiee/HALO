package com.ptc.halo.dtoRequest;

/** A single allowed field; no account identity or protected user properties. */
public record StudentAcademicUpdateRequest(Field field, String value) {
    public enum Field { STUDENT_ID, SECTION, YEAR_LEVEL }
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unsupported academic update property");
    }
}
