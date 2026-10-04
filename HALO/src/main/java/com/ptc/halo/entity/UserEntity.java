package com.ptc.halo.entity;

import com.ptc.halo.enums.Role;
import com.ptc.halo.enums.Status;
import jakarta.persistence.*;

@Entity
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;
    private String name;
    private String email;
    private String password;
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long tokenVersion = 0;
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean mustChangePassword = false;
    public long getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(long value) { tokenVersion = value; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean value) { mustChangePassword = value; }


    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private Status status;

    public UserEntity() {
    }

    public Long getId() {
        return Id;
    }

    public void setId(Long id) {
        Id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
