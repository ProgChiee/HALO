package com.ptc.halo;
import com.ptc.halo.component.SuperAdminInitializer;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import com.ptc.halo.dtoRequest.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SuperAdminSecurityTest {
    String key() { byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes); return Base64.getEncoder().encodeToString(bytes); }
    UserEntity account() { var u = new UserEntity(); u.setEmail("security@example.test"); u.setName("Security test"); u.setRole(Role.SUPER_ADMIN); u.setStatus(Status.ACTIVE); return u; }
    @Test void externalKeyRequiredAndRotationInvalidatesTokens() {
        assertThrows(IllegalStateException.class, () -> new JwtService(""));
        assertThrows(IllegalStateException.class, () -> new JwtService("invalid!"));
        assertThrows(IllegalStateException.class, () -> new JwtService(Base64.getEncoder().encodeToString(new byte[8])));
        var jwt = new JwtService(key()); var user = account(); var token = jwt.generateToken(user);
        assertTrue(jwt.isCurrent(token, new CustomUserDetails(user)));
        assertThrows(io.jsonwebtoken.JwtException.class, () -> new JwtService(key()).extractUsername(token));
    }
    @Test void passwordChangeRevokesOldTokenAndNewLoginWorks() {
        var users = mock(UserRepository.class); var encoder = new BCryptPasswordEncoder();
        var user = account(); user.setMustChangePassword(true); user.setPassword(encoder.encode("Temporary-test-123"));
        when(users.findForPasswordChange(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var jwt = new JwtService(key()); var old = jwt.generateToken(user);
        var service = new PasswordResetService(mock(PasswordResetOtpRepository.class), users, mock(EmailService.class), encoder);
        service.changePassword(user, "Temporary-test-123", "Replacement-test-123");
        assertFalse(jwt.isCurrent(old, new CustomUserDetails(user))); assertFalse(user.isMustChangePassword());
        assertTrue(encoder.matches("Replacement-test-123", user.getPassword()));
        var details = mock(CustomUserDetailsService.class);
        when(details.loadUserByUsername(user.getEmail())).thenReturn(new CustomUserDetails(user));
        var provider = new org.springframework.security.authentication.dao.DaoAuthenticationProvider();
        provider.setUserDetailsService(details); provider.setPasswordEncoder(encoder);
        AuthenticationManager manager = new org.springframework.security.authentication.ProviderManager(provider);
        var auth = new AuthService(users, mock(StudentProfileRepository.class), encoder, manager, jwt);
        var request = new LoginRequest(); request.setEmail(user.getEmail()); request.setPassword("Replacement-test-123");
        var response = auth.login(request);
        assertTrue(jwt.isCurrent(response.getToken(), new CustomUserDetails(user)));
    }
    @Test void missingBootstrapConfigurationFailsOnlyForNewInstallation() {
        var users = mock(UserRepository.class);
        assertThrows(IllegalStateException.class, () -> new SuperAdminInitializer(users, new BCryptPasswordEncoder(), "", "").run());
        verify(users, never()).save(any());
    }
    @Test void externalPropertyWiresJwtServiceAndMissingPropertyFailsStartup() {
        var runner = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(JwtService.class);
        runner.run(context -> assertNotNull(context.getStartupFailure()));
        runner.withPropertyValues("jwt.secret=" + key()).run(context -> assertNotNull(context.getBean(JwtService.class)));
    }
    @Test void resetAlsoRevokesOldToken() {
        var users = mock(UserRepository.class); var otps = mock(PasswordResetOtpRepository.class);
        var encoder = new BCryptPasswordEncoder(); var user = account(); user.setId(5L); user.setPassword(encoder.encode("Old-test-123"));
        when(users.findForPasswordChange(user.getEmail())).thenReturn(Optional.of(user));
        var otp = new PasswordResetOtpEntity(); otp.setOtp("123456"); otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(otps.findByUserId(5L)).thenReturn(Optional.of(otp));
        var jwt = new JwtService(key()); var old = jwt.generateToken(user);
        new PasswordResetService(otps, users, mock(EmailService.class), encoder).resetPassword(user.getEmail(), "123456", "New-test-123");
        assertFalse(jwt.isCurrent(old, new CustomUserDetails(user))); verify(otps).deleteByUserId(5L);
    }
    @Test void bootstrapEncodesPasswordAndDoesNotOverwriteExistingAccount() throws Exception {
        var users = mock(UserRepository.class); var encoder = new BCryptPasswordEncoder();
        new SuperAdminInitializer(users, encoder, " Bootstrap@Example.test ", "Temporary-test-123").run();
        var capture = org.mockito.ArgumentCaptor.forClass(UserEntity.class); verify(users).save(capture.capture());
        assertEquals("bootstrap@example.test", capture.getValue().getEmail());
        assertTrue(encoder.matches("Temporary-test-123", capture.getValue().getPassword()));
        assertTrue(capture.getValue().isMustChangePassword());
        reset(users); when(users.existsByRole(Role.SUPER_ADMIN)).thenReturn(true);
        new SuperAdminInitializer(users, encoder, "", "").run(); verify(users, never()).save(any());
    }
    @Test void backendBlocksTemporaryAccountAndRevokedToken() throws Exception {
        var jwt = new JwtService(key()); var user = account(); user.setMustChangePassword(true);
        var details = mock(CustomUserDetailsService.class); when(details.loadUserByUsername(user.getEmail())).thenReturn(new CustomUserDetails(user));
        var filter = new JwtAuthenticationFilter(jwt, details); var token = jwt.generateToken(user);
        try {
            var req = new MockHttpServletRequest("GET", "/api/super-admin/admins"); req.setServletPath("/api/super-admin/admins"); req.addHeader("Authorization", "Bearer " + token);
            var res = new MockHttpServletResponse(); filter.doFilter(req, res, new MockFilterChain());
            assertEquals(403, res.getStatus()); assertTrue(res.getContentAsString().contains("PASSWORD_CHANGE_REQUIRED"));
            req = new MockHttpServletRequest("POST", "/api/auth/change-password"); req.setServletPath("/api/auth/change-password"); req.addHeader("Authorization", "Bearer " + token);
            res = new MockHttpServletResponse(); filter.doFilter(req, res, new MockFilterChain()); assertEquals(200, res.getStatus());
            SecurityContextHolder.clearContext(); user.setTokenVersion(1);
            req = new MockHttpServletRequest("GET", "/api/super-admin/profile"); req.setServletPath("/api/super-admin/profile"); req.addHeader("Authorization", "Bearer " + token);
            res = new MockHttpServletResponse(); filter.doFilter(req, res, new MockFilterChain()); assertEquals(401, res.getStatus());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
