package com.ptc.halo.repository;

import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.Role;
import com.ptc.halo.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from UserEntity u where u.email = :email")
    Optional<UserEntity> findForPasswordChange(@org.springframework.data.repository.query.Param("email") String email);

    @org.springframework.data.jpa.repository.Query("select count(u) > 0 from UserEntity u where lower(trim(u.email)) = :email and (:excludedId is null or u.id <> :excludedId)")
    boolean existsNormalizedEmail(@org.springframework.data.repository.query.Param("email") String email,
                                  @org.springframework.data.repository.query.Param("excludedId") Long excludedId);

        boolean existsByEmail(String email);

    Optional<UserEntity> findByEmail(String email);

    List<UserEntity> findByRole(Role role);

    boolean existsByRole(Role role);

    long countByRole(Role role);

    long countByStatus(Status status);
}
