package com.ptc.halo.repository;

import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.Role;
import com.ptc.halo.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findForAssessmentLifecycle(@org.springframework.data.repository.query.Param("id") Long id);

    // Scalar aggregates avoid multiplying counts across progress, attempts, and badges.
    // No lazy entities or per-student repository calls are used by the summary page.
    @org.springframework.data.jpa.repository.Query(value = """
        select new com.ptc.halo.dtoResponse.ProfessorStudentSummary(
            u.id, u.name, profile.section,
            (select count(p.id) from StudentModuleProgressEntity p
             where p.student.id = u.id and p.completed = true
             and p.module.week.subject.professor.user.id = :professorId),
            (select count(a.id) from AssessmentAttemptEntity a
             where a.student.id = u.id and a.passed = true
             and a.assessment.module.week.subject.professor.user.id = :professorId),
            (select count(b.id) from StudentBadgeEntity b where b.student.id = u.id))
        from UserEntity u left join StudentProfileEntity profile on profile.user.id = u.id
        where u.role = com.ptc.halo.enums.Role.STUDENT
        order by u.id asc
        """, countQuery = "select count(u.id) from UserEntity u where u.role = com.ptc.halo.enums.Role.STUDENT")
    org.springframework.data.domain.Page<com.ptc.halo.dtoResponse.ProfessorStudentSummary> findProfessorProgressSummaries(
            @org.springframework.data.repository.query.Param("professorId") Long professorId,
            org.springframework.data.domain.Pageable pageable);

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
