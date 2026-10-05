package com.ptc.halo.repository;

import com.ptc.halo.entity.WeekEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeekRepository
        extends JpaRepository<WeekEntity, Long> {
    interface StudentWeekRow {
        Long getSubjectId();
        Long getWeekId();
        Integer getWeekNumber();
        String getTitle();
        Long getModuleId();
        Long getCompletedCount();
    }

    @org.springframework.data.jpa.repository.Query("""
        select w.subject.id as subjectId, w.id as weekId, w.weekNumber as weekNumber,
               w.title as title, m.id as moduleId, count(distinct p.id) as completedCount
        from WeekEntity w
        left join AiLearningModuleEntity m on m.week = w
            and m.status = com.ptc.halo.enums.LessonStatus.APPROVED
            and m.aiGenerationStatus = com.ptc.halo.enums.AiGenerationStatus.COMPLETED
        left join StudentModuleProgressEntity p on p.module = m
            and p.student.id = :studentId and p.completed = true
        where w.subject.id in :subjectIds
        group by w.subject.id, w.id, w.weekNumber, w.title, m.id
        order by w.subject.id, w.weekNumber, w.id
        """)
    List<StudentWeekRow> findStudentWeekRows(
            @org.springframework.data.repository.query.Param("subjectIds") List<Long> subjectIds,
            @org.springframework.data.repository.query.Param("studentId") Long studentId);
    java.util.Optional<WeekEntity> findByIdAndSubject_Professor_User_Id(Long id, Long userId);

    boolean existsBySubject_IdAndWeekNumber(Long subjectId, Integer weekNumber);
    boolean existsBySubject_IdAndWeekNumberAndIdNot(Long subjectId, Integer weekNumber, Long id);

    boolean existsBySubject_Id(Long subjectId);

    List<WeekEntity> findBySubject_Id(Long subjectId);

    List<WeekEntity>
    findBySubject_IdOrderByWeekNumberAsc(Long subjectId);
}
