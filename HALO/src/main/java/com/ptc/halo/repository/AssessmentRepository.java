package com.ptc.halo.repository;

import com.ptc.halo.entity.AssessmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssessmentRepository
        extends JpaRepository<AssessmentEntity, Long> {
    @org.springframework.data.jpa.repository.Query("select count(a) from AssessmentEntity a where a.module.week.subject.professor.user.id=:professorId and a.status in (com.ptc.halo.enums.AssessmentStatus.AVAILABLE,com.ptc.halo.enums.AssessmentStatus.INACTIVE)")
    long countByModule_Week_Subject_Professor_User_Id(Long professorId);


    // The current published quiz only; old attempts retain their direct assessment FK.
    @org.springframework.data.jpa.repository.Query("select a from AssessmentEntity a where a.module.id=:moduleId and a.status in (com.ptc.halo.enums.AssessmentStatus.AVAILABLE,com.ptc.halo.enums.AssessmentStatus.INACTIVE)")
    Optional<AssessmentEntity> findByModuleId(@org.springframework.data.repository.query.Param("moduleId") Long moduleId);
    Optional<AssessmentEntity> findTopByModuleIdAndStatusOrderByQuizVersionDesc(Long moduleId,com.ptc.halo.enums.AssessmentStatus status);
    @org.springframework.data.jpa.repository.Query("select coalesce(max(a.quizVersion),0) from AssessmentEntity a where a.module.id=:id")
    int latestVersion(@org.springframework.data.repository.query.Param("id") Long id);
}