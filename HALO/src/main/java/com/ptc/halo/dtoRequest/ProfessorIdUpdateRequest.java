package com.ptc.halo.dtoRequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Same ID rules as ProfessorRequest/ProfessorUpdateRequest; no account selector. */
public record ProfessorIdUpdateRequest(@NotBlank @Size(max = 255) String professorId) {
    public ProfessorIdUpdateRequest { professorId = professorId == null ? null : professorId.trim(); }
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unsupported profile property");
    }
}
