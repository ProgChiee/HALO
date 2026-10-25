package com.ptc.halo;

import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.Role;
import com.ptc.halo.enums.Status;
import com.ptc.halo.security.CustomUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/** Test principals carry the same immutable identity as production JWT authentication. */
final class TestIdentity {
    static CustomUserDetails details(long id, String email, String role) {
        var user = new UserEntity(); user.setId(id); user.setEmail(email);
        user.setRole(Role.valueOf(role)); user.setStatus(Status.ACTIVE);
        return new CustomUserDetails(user);
    }
    static UsernamePasswordAuthenticationToken auth(UserEntity user) {
        var principal = new CustomUserDetails(user);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}
