package com.ptc.halo.controller;

import com.ptc.halo.dtoRequest.*;
import com.ptc.halo.dtoResponse.LoginResponse;
import com.ptc.halo.dtoResponse.StudentResponse;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.service.AuthService;
import com.ptc.halo.service.PasswordResetService;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final UserRepository userRepository;


    public AuthController(AuthService authService, PasswordResetService passwordResetService, UserRepository userRepository) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.userRepository = userRepository;
    }


    @PostMapping("/register/student")
    public ResponseEntity<StudentResponse> registerStudent(@RequestBody StudentRequest studentRequest){
    StudentResponse response = authService.registerStudent(studentRequest);

    return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> loginUser(@RequestBody LoginRequest loginRequest){
    LoginResponse response = authService.login(loginRequest);

    return ResponseEntity.ok(response);
    }
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(
            @jakarta.validation.Valid @RequestBody ForgotPasswordRequest request) {

        runReset(() -> passwordResetService.sendOtp(request.getEmail()));

        return ResponseEntity.ok(
                "Password reset OTP sent successfully"
        );
    }
    @PostMapping("/forgot-password/resend")
    public ResponseEntity<String> resendPasswordOtp(
            @jakarta.validation.Valid @RequestBody ResendPasswordOtpRequest request) {

        runReset(() -> passwordResetService.sendOtp(request.getEmail()));

        return ResponseEntity.ok(
                "New password reset OTP sent successfully"
        );
    }
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(
            @jakarta.validation.Valid @RequestBody ResetPasswordRequest request) {

        runReset(() -> passwordResetService.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword()));

        return ResponseEntity.ok(
                "Password reset successfully"
        );
    }
    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @jakarta.validation.Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        UserEntity user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new RuntimeException("User not found")
                );

        passwordResetService.changePassword(
                user,
                request.getCurrentPassword(),
                request.getNewPassword()
        );

        return ResponseEntity.ok(
                "Password changed successfully"
        );
    }


    private void runReset(Runnable operation) {
        try { operation.run(); }
        catch (com.ptc.halo.service.PasswordResetException | org.springframework.web.server.ResponseStatusException
                | org.springframework.security.core.AuthenticationException | org.springframework.security.access.AccessDeniedException error) { throw error; }
        catch (RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger(AuthController.class).error("Password reset failed; exceptionType={}", error.getClass().getSimpleName());
            throw new com.ptc.halo.service.PasswordResetException(500);
        }
    }
}
