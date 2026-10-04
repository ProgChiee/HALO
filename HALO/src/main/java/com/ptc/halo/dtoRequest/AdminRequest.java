package com.ptc.halo.dtoRequest;

import jakarta.validation.constraints.*;

public class AdminRequest {

    @NotBlank @Size(max = 100)
    private String name;
    @NotBlank @Email @Size(max = 254)
    private String email;
    @NotBlank @Size(min = 12, max = 72)
    private String password;

    @jakarta.validation.constraints.AssertTrue(message = "password must be at most 72 UTF-8 bytes")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
    public AdminRequest() {
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
}
