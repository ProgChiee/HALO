package com.ptc.halo.repository;

import com.ptc.halo.entity.AdminActingSessionEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AdminActingSessionRepository extends JpaRepository<AdminActingSessionEntity, String> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AdminActingSessionEntity s where s.admin.id=:actorId and s.adminTokenVersion=:version and s.revokedAt is null and s.expiresAt>:now and s.target.status=com.ptc.halo.enums.Status.ACTIVE and s.target.role=s.targetRole and s.target.tokenVersion=s.targetTokenVersion")
    java.util.List<AdminActingSessionEntity> findActiveModesForUpdate(@Param("actorId") Long actorId,@Param("version") long version,@Param("now") java.time.Instant now);
    @EntityGraph(attributePaths = {"admin", "target"})
    Optional<AdminActingSessionEntity> findByIdAndAdmin_Id(String id, Long adminId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AdminActingSessionEntity s where s.id = :id and s.admin.id = :adminId")
    Optional<AdminActingSessionEntity> findForRevocation(@Param("id") String id, @Param("adminId") Long adminId);
}
