package com.ptc.halo.dtoRequest;
import jakarta.validation.constraints.*;

public class ProfessorUpdateRequest {

    @NotBlank @Size(max = 100)
    private String name;

    @NotBlank @Email @Size(max = 254)
    private String email;

    @NotBlank @Size(max = 255)
    private String professorId;


    public ProfessorUpdateRequest() {
    }


    public String getName() {
        return name;
    }


    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }


    public String getEmail() {
        return email;
    }


    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }


    public String getProfessorId() {
        return professorId;
    }


    public void setProfessorId(String professorId) {
        this.professorId = professorId == null ? null : professorId.trim();
    }
}