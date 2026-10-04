package com.ptc.halo.dtoRequest;
import jakarta.validation.constraints.*;

public class ProfessorRequest {

    @NotBlank @Size(max = 100)
    private String name;
    @NotBlank @Email @Size(max = 254)
    private String email;
    @NotBlank @Size(min = 8, max = 72)
    private String password;
    @NotBlank @Size(max = 255)
    private String professorId;


    @AssertTrue(message = "password must be at most 72 UTF-8 bytes")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
    public ProfessorRequest() {
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


    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getProfessorId() {
        return professorId;
    }

    public void setProfessorId(String professorId) {
        this.professorId = professorId == null ? null : professorId.trim();
    }
}
