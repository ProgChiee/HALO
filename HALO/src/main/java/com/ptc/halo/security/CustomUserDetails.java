package com.ptc.halo.security;

import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.Status;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class CustomUserDetails implements UserDetails {
    private final UserEntity user;
    private final Long userId;
    private final long tokenVersion;

    public CustomUserDetails(UserEntity user) {
        this.user = user;
        this.userId = user.getId();
        this.tokenVersion = user.getTokenVersion();
    }

    public Long getUserId() { return userId; }
    public long getTokenVersion() { return tokenVersion; }
    public boolean isMustChangePassword() { return user.isMustChangePassword(); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
                new SimpleGrantedAuthority(
                        "ROLE_" + user.getRole().name()
                )
        );
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == Status.ACTIVE;
    }
}
