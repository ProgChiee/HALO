package com.ptc.halo.component;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.Role;
import com.ptc.halo.enums.Status;
import com.ptc.halo.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
public class SuperAdminInitializer implements CommandLineRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;
    public SuperAdminInitializer(UserRepository users, PasswordEncoder encoder,
            @Value("${SUPER_ADMIN_EMAIL:}") String email,
            @Value("${SUPER_ADMIN_PASSWORD:}") String password) {
        this.users = users; this.encoder = encoder; this.email = email; this.password = password;
    }
    @Override public void run(String... args) {
        if (users.existsByRole(Role.SUPER_ADMIN)) return;
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") || password == null || password.isBlank() || password.length() < 12)
            throw new IllegalStateException("First bootstrap requires SUPER_ADMIN_EMAIL and SUPER_ADMIN_PASSWORD (at least 12 characters)");
        if (users.existsByEmail(normalized)) throw new IllegalStateException("Bootstrap email is already assigned; existing accounts were not changed");
        UserEntity user = new UserEntity();
        user.setEmail(normalized); user.setName("HALO SUPER_ADMIN");
        user.setPassword(encoder.encode(password)); user.setRole(Role.SUPER_ADMIN);
        user.setStatus(Status.ACTIVE); user.setMustChangePassword(true);
        users.save(user);
    }
}
