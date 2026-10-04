package com.ptc.halo.component;

import com.ptc.halo.enums.Role;
import com.ptc.halo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Explicit local recovery only. Remove both reset variables/profile immediately after use. */
@Component
@Profile("local & local-super-admin-reset & !prod & !production")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalSuperAdminPasswordReset implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalSuperAdminPasswordReset.class);
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transaction;
    private final String email;
    private final String password;

    public LocalSuperAdminPasswordReset(UserRepository users, PasswordEncoder encoder,
            PlatformTransactionManager transactions,
            @Value("${LOCAL_SUPER_ADMIN_RESET_EMAIL:}") String email,
            @Value("${LOCAL_SUPER_ADMIN_RESET_PASSWORD:}") String password) {
        this.users = users; this.encoder = encoder;
        this.transaction = new TransactionTemplate(transactions);
        this.email = email; this.password = password;
    }

    @Override public void run(String... args) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || password == null || password.isBlank() || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Local reset requires an existing account email and a password of at least 12 characters, at most 72 UTF-8 bytes");
        }
        transaction.executeWithoutResult(status -> {
            var user = users.findForPasswordChange(normalized)
                    .orElseThrow(() -> new IllegalStateException("Local reset target does not exist; no account was created"));
            if (user.getRole() != Role.SUPER_ADMIN)
                throw new IllegalStateException("Local reset target is not a Super Admin; no account was changed");
            long nextVersion = Math.addExact(user.getTokenVersion(), 1);
            String encoded = encoder.encode(password);
            user.setPassword(encoded);
            user.setTokenVersion(nextVersion);
            user.setMustChangePassword(true);
            users.saveAndFlush(user);
        });
        // Execute only after the transaction commits. Never log identity, password or hash.
        log.info("Local Super Admin password reset completed.");
    }
}
