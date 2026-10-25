package com.ptc.halo.service;

import com.ptc.halo.dtoResponse.LoginResponse;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.Status;
import com.ptc.halo.security.JwtService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Revalidates the password-authenticated identity before signing, under the account lock. */
@Service
public class LoginTokenIssuer {
    @PersistenceContext private EntityManager entityManager;
    private final JwtService jwt;

    public LoginTokenIssuer(JwtService jwt) { this.jwt = jwt; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoginResponse issue(Long userId, String verifiedHash, long verifiedVersion, Set<String> verifiedAuthorities) {
        if (userId == null) throw new BadCredentialsException("Invalid credentials");
        UserEntity user = entityManager.find(UserEntity.class, userId, LockModeType.PESSIMISTIC_WRITE);
        if (user == null) throw new BadCredentialsException("Invalid credentials");
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        if (user.getStatus() != Status.ACTIVE || user.getRole() == null
                || !Objects.equals(verifiedHash, user.getPassword())
                || verifiedVersion != user.getTokenVersion()
                || !verifiedAuthorities.equals(Set.of("ROLE_" + user.getRole().name()))) {
            throw new BadCredentialsException("Account changed during login. Please sign in again.");
        }
        LoginResponse response = new LoginResponse();
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setToken(jwt.generateToken(user));
        response.setMustChangePassword(user.isMustChangePassword());
        return response;
    }
}
