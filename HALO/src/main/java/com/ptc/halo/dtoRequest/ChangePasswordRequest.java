package com.ptc.halo.dtoRequest;

public class ChangePasswordRequest {

    @jakarta.validation.constraints.NotBlank
    private String currentPassword;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(min = 8, max = 72)
    private String newPassword;

    @jakarta.validation.constraints.AssertTrue(message = "new password must be at most 72 UTF-8 bytes")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNewPasswordWithinByteLimit() {
        return newPassword == null || newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
    public ChangePasswordRequest() {
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}