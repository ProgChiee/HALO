package com.ptc.halo.service;

import com.ptc.halo.entity.PasswordResetOtpEntity;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.repository.PasswordResetOtpRepository;
import com.ptc.halo.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class PasswordResetService {

    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            PasswordResetOtpRepository passwordResetOtpRepository,
            UserRepository userRepository,
            EmailService emailService, PasswordEncoder passwordEncoder) {

        this.passwordResetOtpRepository =
                passwordResetOtpRepository;

        this.userRepository =
                userRepository;

        this.emailService =
                emailService;
        this.passwordEncoder = passwordEncoder;
    }


    @Transactional
    public void sendOtp(String email) {

        UserEntity user =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new PasswordResetException(400)
                        );

        String otp = generateOtp();

        PasswordResetOtpEntity otpEntity =
                passwordResetOtpRepository
                        .findByUserId(user.getId())
                        .orElse(new PasswordResetOtpEntity());

        otpEntity.setUser(user);
        otpEntity.setOtp(otp);
        otpEntity.setCreatedAt(LocalDateTime.now());

        // OTP valid for 10 minutes
        otpEntity.setExpiresAt(
                LocalDateTime.now().plusMinutes(10)
        );

        passwordResetOtpRepository.save(otpEntity);

        emailService.sendPasswordResetOtp(
                user.getEmail(),
                otp
        );
    }


    private String generateOtp() {

        int number =
                secureRandom.nextInt(900000) + 100000;

        return String.valueOf(number);
    }
    @Transactional
    public void resetPassword(
            String email,
            String otp,
            String newPassword) {

        UserEntity user = userRepository.findForPasswordChange(email)
                .orElseThrow(() ->
                        new PasswordResetException(400)
                );

        PasswordResetOtpEntity otpEntity =
                passwordResetOtpRepository
                        .findByUserId(user.getId())
                        .orElseThrow(() ->
                                new PasswordResetException(400)
                        );

        if (!otpEntity.getOtp().equals(otp)) {
            throw new PasswordResetException(400);
        }

        if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new PasswordResetException(400);
        }

        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 8 || passwordEncoder.matches(newPassword, user.getPassword()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_NEW_PASSWORD");
        user.setTokenVersion(Math.addExact(user.getTokenVersion(), 1));
        user.setMustChangePassword(false);
        user.setPassword(
                passwordEncoder.encode(newPassword)
        );

        userRepository.save(user);

        passwordResetOtpRepository.deleteByUserId(user.getId());
    }
    @Transactional
    public void changePassword(
            UserEntity user,
            String currentPassword,
            String newPassword) {

        user = userRepository.findForPasswordChange(user.getEmail()).orElseThrow();
        if (!passwordEncoder.matches(
                currentPassword,
                user.getPassword())) {

            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "INCORRECT_CURRENT_PASSWORD");
        }

        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 8 || passwordEncoder.matches(newPassword, user.getPassword()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_NEW_PASSWORD");
        user.setTokenVersion(Math.addExact(user.getTokenVersion(), 1));
        user.setMustChangePassword(false);
        user.setPassword(
                passwordEncoder.encode(newPassword)
        );

        userRepository.save(user);
    }
}