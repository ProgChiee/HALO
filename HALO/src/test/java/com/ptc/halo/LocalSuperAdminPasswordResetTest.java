package com.ptc.halo;

import com.ptc.halo.component.LocalSuperAdminPasswordReset;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LocalSuperAdminPasswordResetTest {
    UserRepository users = mock(UserRepository.class);
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    PlatformTransactionManager transactions() {
        var tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        return tx;
    }
    @Test void existingAccountIsEncodedAndTokensRevokedWithoutChangingRoleOrStatus() {
        var user = new UserEntity(); user.setId(42L); user.setEmail("local@example.test");
        user.setRole(Role.SUPER_ADMIN); user.setStatus(Status.ACTIVE); user.setTokenVersion(4);
        byte[] key = new byte[32]; new java.security.SecureRandom().nextBytes(key);
        var jwt = new JwtService(Base64.getEncoder().encodeToString(key)); var oldToken = jwt.generateToken(user);
        when(users.findForPasswordChange("local@example.test")).thenReturn(Optional.of(user));
        var tx = transactions();
        new LocalSuperAdminPasswordReset(users, encoder, tx, " Local@Example.test ", "Temporary-test-123").run();
        assertTrue(encoder.matches("Temporary-test-123", user.getPassword()));
        assertNotEquals("Temporary-test-123", user.getPassword());
        assertEquals(5, user.getTokenVersion()); assertTrue(user.isMustChangePassword());
        assertEquals(Role.SUPER_ADMIN, user.getRole()); assertEquals(Status.ACTIVE, user.getStatus());
        assertEquals(42L, user.getId()); assertFalse(jwt.isCurrent(oldToken, new CustomUserDetails(user)));
        verify(users).saveAndFlush(user); verify(tx).commit(any());
    }
    @Test void missingAndWrongRoleTargetsAreNeverSaved() {
        assertThrows(IllegalStateException.class, () -> new LocalSuperAdminPasswordReset(users, encoder, transactions(), "missing@example.test", "Temporary-test-123").run());
        var user = new UserEntity(); user.setRole(Role.ADMIN); user.setPassword("unchanged");
        when(users.findForPasswordChange("admin@example.test")).thenReturn(Optional.of(user));
        assertThrows(IllegalStateException.class, () -> new LocalSuperAdminPasswordReset(users, encoder, transactions(), "admin@example.test", "Temporary-test-123").run());
        assertEquals("unchanged", user.getPassword()); verify(users, never()).saveAndFlush(any());
    }
    @Test void incompleteConfigurationFailsBeforeDatabaseAccess() {
        assertThrows(IllegalStateException.class, () -> new LocalSuperAdminPasswordReset(users, encoder, transactions(), "", "").run());
        verifyNoInteractions(users);
    }
    @Test void runnerRequiresBothExplicitProfilesAndExcludesProduction() {
        var runner = new ApplicationContextRunner().withUserConfiguration(LocalSuperAdminPasswordReset.class)
                .withBean(UserRepository.class, () -> users)
                .withBean(PasswordEncoder.class, () -> encoder)
                .withBean(PlatformTransactionManager.class, this::transactions);
        for (String profile : List.of("default", "local", "local-super-admin-reset", "local,local-super-admin-reset,prod", "local,local-super-admin-reset,production")) {
            runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> assertEquals(0, context.getBeansOfType(LocalSuperAdminPasswordReset.class).size()));
        }
        runner.withPropertyValues("spring.profiles.active=local,local-super-admin-reset")
                .run(context -> assertEquals(1, context.getBeansOfType(LocalSuperAdminPasswordReset.class).size()));
        verifyNoInteractions(users);
    }
}
