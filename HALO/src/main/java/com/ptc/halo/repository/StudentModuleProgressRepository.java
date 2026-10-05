package com.ptc.halo.repository;

import com.ptc.halo.entity.StudentModuleProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentModuleProgressRepository
        extends JpaRepository<StudentModuleProgressEntity, Long> {

    @org.springframework.data.jpa.repository.Query("""
        select count(distinct p.module.id) from StudentModuleProgressEntity p
        where p.student.id = :studentId and p.completed = true
        and p.module.status = com.ptc.halo.enums.LessonStatus.APPROVED
        and p.module.aiGenerationStatus = com.ptc.halo.enums.AiGenerationStatus.COMPLETED
        and p.module.week.subject.yearLevel = (select sp.yearLevel from StudentProfileEntity sp where sp.user.id = :studentId)
        """)
    long countEligibleCompletedModules(@org.springframework.data.repository.query.Param("studentId") Long studentId);

    @org.springframework.data.jpa.repository.Query("""
        select count(s.id) from SubjectEntity s
        where s.yearLevel = (select sp.yearLevel from StudentProfileEntity sp where sp.user.id = :studentId)
        and exists (select m.id from AiLearningModuleEntity m where m.week.subject = s
            and m.status = com.ptc.halo.enums.LessonStatus.APPROVED
            and m.aiGenerationStatus = com.ptc.halo.enums.AiGenerationStatus.COMPLETED)
        and not exists (select m.id from AiLearningModuleEntity m where m.week.subject = s
            and m.status = com.ptc.halo.enums.LessonStatus.APPROVED
            and m.aiGenerationStatus = com.ptc.halo.enums.AiGenerationStatus.COMPLETED
            and not exists (select p.id from StudentModuleProgressEntity p
                where p.module = m and p.student.id = :studentId and p.completed = true))
        """)
    long countMasteredEligibleSubjects(@org.springframework.data.repository.query.Param("studentId") Long studentId);

    long countByStudentIdAndCompletedTrueAndModule_Week_Subject_Professor_User_Id(Long studentId, Long professorId);

    Optional<StudentModuleProgressEntity>
    findByStudentIdAndModuleId(
            Long studentId,
            Long moduleId
    );

    List<StudentModuleProgressEntity>
    findByStudentIdOrderByCompletedAtDesc(
            Long studentId
    );

    long countByStudentIdAndCompletedTrue(
            Long studentId
    );
}
