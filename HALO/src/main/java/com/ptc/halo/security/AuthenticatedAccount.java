package com.ptc.halo.security;

import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.Status;
import com.ptc.halo.repository.UserRepository;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;

/** Resolve request identity only from the server-authenticated immutable account ID. */
public final class AuthenticatedAccount {
    private AuthenticatedAccount() {}

    public static CustomUserDetails principal(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CustomUserDetails p)
                || p.getUserId() == null || p.getUserId() <= 0)
            throw new BadCredentialsException("Authentication required");
        return p;
    }

    public static Optional<UserEntity> find(UserRepository users, Authentication auth) {
        var p = principal(auth);
        return Optional.of(validate(auth, p, users.findById(p.getUserId())
                .orElseThrow(() -> new BadCredentialsException("Authentication required"))));
    }

    /** Caller must hold a transaction for credential/session mutations. */
    public static UserEntity lock(UserRepository users, Authentication auth) {
        var p = principal(auth);
        return validate(auth, p, users.findForAssessmentLifecycle(p.getUserId())
                .orElseThrow(() -> new BadCredentialsException("Authentication required")));
    }

    private static UserEntity validate(Authentication auth, CustomUserDetails p, UserEntity user) {
        if (!p.getUserId().equals(user.getId()) || p.getTokenVersion() != user.getTokenVersion())
            throw new BadCredentialsException("Invalid or expired token");
        if (user.getStatus() != Status.ACTIVE || user.getRole() == null
                || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_" + user.getRole().name())))
            throw new AccessDeniedException("Account unavailable");
        return user;
    }
}
