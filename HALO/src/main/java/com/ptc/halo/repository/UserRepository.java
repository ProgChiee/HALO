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

    @org.springframework.data.jpa.repository.Query("""
        select new com.ptc.halo.dtoResponse.AdminActingTargetResponse(u.id, u.name, u.email, u.role)
        from UserEntity u where u.role = :role and u.status = com.ptc.halo.enums.Status.ACTIVE
        and (:search = '' or locate(:search, lower(coalesce(u.name, ''))) > 0
             or locate(:search, lower(coalesce(u.email, ''))) > 0)
        """)
    org.springframework.data.domain.Page<com.ptc.halo.dtoResponse.AdminActingTargetResponse> findActingTargets(
        @org.springframework.data.repository.query.Param("role") Role role,
        @org.springframework.data.repository.query.Param("search") String search,
        org.springframework.data.domain.Pageable pageable);

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
        and exists (select s.id from SubjectEntity s where s.professor.user.id=:professorId and s.yearLevel=profile.yearLevel)
        order by u.id asc
        """, countQuery = "select count(u.id) from UserEntity u join StudentProfileEntity profile on profile.user.id=u.id where u.role = com.ptc.halo.enums.Role.STUDENT and exists (select s.id from SubjectEntity s where s.professor.user.id=:professorId and s.yearLevel=profile.yearLevel)")
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
