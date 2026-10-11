package com.ptc.halo.dtoRequest;

public class ResendPasswordOtpRequest {

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Email
    @jakarta.validation.constraints.Size(max = 254)
    private String email;

    public ResendPasswordOtpRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}