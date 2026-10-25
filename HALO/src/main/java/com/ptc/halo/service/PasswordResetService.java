package com.ptc.halo.service;

import com.ptc.halo.entity.PasswordResetOtpEntity;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.repository.PasswordResetOtpRepository;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.enums.AuthEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class PasswordResetService {
    private final AuthAuditService audit;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetProtection protection;
    private final PasswordResetThrottle throttle;
    private final TransactionTemplate transactions;

    public PasswordResetService(PasswordResetOtpRepository otps, UserRepository users, EmailService mail,
            PasswordEncoder encoder, AuthAuditService audit, PasswordResetProtection protection,
            PasswordResetThrottle throttle, PlatformTransactionManager manager) {
        this.passwordResetOtpRepository=otps; this.userRepository=users; this.emailService=mail;
        this.passwordEncoder=encoder; this.audit=audit; this.protection=protection; this.throttle=throttle;
        this.transactions=new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    private String identifier(String email) {
        String normalized=email.strip().toLowerCase(Locale.ROOT);
        // Resolve database-collation aliases to the same account bucket (not just casing/whitespace).
        return userRepository.findByEmail(normalized).map(UserEntity::getEmail)
                .map(value -> value.strip().toLowerCase(Locale.ROOT)).orElse(normalized);
    }
    private record Delivery(String email,String code) {}

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public void sendOtp(String email) { sendOtp(email,"internal"); }

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public int sendOtp(String email,String remoteAddress) {
        String normalized=identifier(email);
        throttle.admit(normalized,remoteAddress,false);
        Delivery delivery=transactions.execute(tx -> {
            // Serialize issuance, verification and password changes on the same durable user lock.
            var user=userRepository.findForPasswordChange(normalized).orElse(null);
            String code=protection.newCode();
            String verifier=protection.verifier(user==null?0:user.getId(),code);
            if(user==null) return null;
            LocalDateTime now=throttle.databaseNow();
            var challenge=passwordResetOtpRepository.findByUserId(user.getId()).orElse(new PasswordResetOtpEntity());
            boolean ongoing=challenge.getExpiresAt()!=null && challenge.getExpiresAt().isAfter(now);
            // Resending must never erase failures or extend an existing challenge's lifetime/lockout.
            if(ongoing && challenge.getFailedAttempts()>=protection.maxAttempts) return null;
            if(!ongoing) { challenge.setFailedAttempts(0); challenge.setCreatedAt(now); challenge.setExpiresAt(now.plusSeconds(protection.ttlSeconds)); }
            challenge.setUser(user); challenge.setOtp(verifier);
            passwordResetOtpRepository.saveAndFlush(challenge);
            return new Delivery(user.getEmail(),code);
        });
        if(delivery!=null) {
            try {
                emailService.sendPasswordResetOtp(delivery.email(),delivery.code());
                audit.record(AuthEvent.PASSWORD_RESET_REQUESTED,delivery.email());
            } catch(RuntimeException error) {
                // Keep admission and challenge committed; do not reveal account existence through SMTP failures.
                org.slf4j.LoggerFactory.getLogger(PasswordResetService.class).warn("Password reset delivery unavailable; exceptionType={}",error.getClass().getSimpleName());
            }
        }
        return protection.cooldownSeconds;
    }

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public void resetPassword(String email,String otp,String newPassword) { resetPassword(email,otp,newPassword,"internal"); }

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public void resetPassword(String email,String otp,String newPassword,String remoteAddress) {
        String normalized=identifier(email);
        throttle.admit(normalized,remoteAddress,true);
        boolean accepted=Boolean.TRUE.equals(transactions.execute(tx -> {
            var user=userRepository.findForPasswordChange(normalized).orElse(null);
            if(user==null) return false;
            var challenge=passwordResetOtpRepository.findByUserId(user.getId()).orElse(null);
            LocalDateTime now=throttle.databaseNow();
            if(challenge==null || !challenge.getExpiresAt().isAfter(now) || challenge.getFailedAttempts()>=protection.maxAttempts) return false;
            if(!protection.matches(user.getId(),otp,challenge.getOtp())) {
                challenge.setFailedAttempts(challenge.getFailedAttempts()+1);
                passwordResetOtpRepository.saveAndFlush(challenge);
                return false; // Commit failure count before returning the public error.
            }
            if(newPassword==null || newPassword.isBlank() || newPassword.length()<8 || newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72 || passwordEncoder.matches(newPassword,user.getPassword()))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_NEW_PASSWORD");
            user.setTokenVersion(Math.addExact(user.getTokenVersion(),1)); user.setMustChangePassword(false);
            user.setPassword(passwordEncoder.encode(newPassword)); userRepository.save(user);
            passwordResetOtpRepository.deleteByUserId(user.getId());
            audit.record(AuthEvent.OTP_VERIFIED,user.getEmail()); audit.record(AuthEvent.PASSWORD_RESET_COMPLETED,user.getEmail());
            return true;
        }));
        if(!accepted) throw new PasswordResetException(400);
    }
    @Transactional
    public void changePassword(
            UserEntity user,
            String currentPassword,
            String newPassword) {

        long verifiedVersion = user.getTokenVersion();
        var verifiedRole = user.getRole();
        user = userRepository.findForAssessmentLifecycle(user.getId()).orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Authentication required"));
        if (user.getStatus() != com.ptc.halo.enums.Status.ACTIVE || user.getTokenVersion() != verifiedVersion || user.getRole() != verifiedRole)
            throw new org.springframework.security.authentication.BadCredentialsException("Account changed. Please sign in again.");
        if (!passwordEncoder.matches(
                currentPassword,
                user.getPassword())) {

            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "INCORRECT_CURRENT_PASSWORD");
        }

        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 8 || newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72 || passwordEncoder.matches(newPassword, user.getPassword()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_NEW_PASSWORD");
        user.setTokenVersion(Math.addExact(user.getTokenVersion(), 1));
        user.setMustChangePassword(false);
        user.setPassword(
                passwordEncoder.encode(newPassword)
        );

        userRepository.save(user);
    }
}
