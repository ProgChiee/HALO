package com.ptc.halo.repository;

import com.ptc.halo.entity.ActivityLogEntity;
import com.ptc.halo.enums.ActivityType;
import com.ptc.halo.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityLogRepository
        extends JpaRepository<ActivityLogEntity, Long> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "user")
    @org.springframework.data.jpa.repository.Query("""
        select l from ActivityLogEntity l join l.user u
        where (u.role = com.ptc.halo.enums.Role.ADMIN
            or (u.role = com.ptc.halo.enums.Role.SUPER_ADMIN and l.activityType = com.ptc.halo.enums.ActivityType.ACCOUNT))
        and (:type is null or l.activityType = :type)
        and (locate(:search, lower(l.action)) > 0 or locate(:search, lower(u.name)) > 0)
        """)
    org.springframework.data.domain.Page<ActivityLogEntity> findSuperAdminLogs(
        @org.springframework.data.repository.query.Param("type") ActivityType type,
        @org.springframework.data.repository.query.Param("search") String search,
        org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "user")
    @org.springframework.data.jpa.repository.Query("""
        select l from ActivityLogEntity l join l.user u
        where (:role is null or u.role = :role) and (:type is null or l.activityType = :type)
        and (locate(:search, lower(l.action)) > 0 or locate(:search, lower(u.name)) > 0)
        """)
    org.springframework.data.domain.Page<ActivityLogEntity> findAdminLogs(
        @org.springframework.data.repository.query.Param("role") Role role,
        @org.springframework.data.repository.query.Param("type") ActivityType type,
        @org.springframework.data.repository.query.Param("search") String search,
        org.springframework.data.domain.Pageable pageable);

    interface AdminActivitySummary {
        Long getAdminId(); Long getAccountActivities(); java.time.LocalDateTime getLastActivity();
    }
    @org.springframework.data.jpa.repository.Query("""
        select l.user.id as adminId,
            sum(case when l.activityType = com.ptc.halo.enums.ActivityType.ACCOUNT then 1L else 0L end) as accountActivities,
            max(l.createdAt) as lastActivity
        from ActivityLogEntity l where l.user.role = com.ptc.halo.enums.Role.ADMIN group by l.user.id
        """)
    List<AdminActivitySummary> summarizeAdminActivity();

    List<ActivityLogEntity>
    findAllByOrderByCreatedAtDesc();

    List<ActivityLogEntity>
    findByUser_RoleOrderByCreatedAtDesc(Role role);

    List<ActivityLogEntity>
    findByActivityTypeOrderByCreatedAtDesc(
            ActivityType activityType
    );

    List<ActivityLogEntity>
    findByUser_RoleAndActivityTypeOrderByCreatedAtDesc(
            Role role,
            ActivityType activityType
    );
    List<ActivityLogEntity>
    findTop10ByOrderByCreatedAtDesc();

    long countByUserIdAndActivityType(
            Long userId,
            ActivityType activityType
    );

    Optional<ActivityLogEntity>
    findTopByUserIdOrderByCreatedAtDesc(
            Long userId
    );
}