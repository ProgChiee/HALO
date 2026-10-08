package com.ptc.halo.repository;

import com.ptc.halo.entity.AdminActingSessionEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AdminActingSessionRepository extends JpaRepository<AdminActingSessionEntity, String> {
    @EntityGraph(attributePaths = {"admin", "target"})
    Optional<AdminActingSessionEntity> findByIdAndAdmin_Id(String id, Long adminId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AdminActingSessionEntity s where s.id = :id and s.admin.id = :adminId")
    Optional<AdminActingSessionEntity> findForRevocation(@Param("id") String id, @Param("adminId") Long adminId);
}
