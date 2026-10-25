package com.ptc.halo.repository;

import com.ptc.halo.entity.WeekEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeekRepository
        extends JpaRepository<WeekEntity, Long> {
    interface ModuleActivity {
        Long getModuleId();
        java.time.LocalDateTime getLastActivityAt();
    }

    @org.springframework.data.jpa.repository.Query(value = """
        select activity.module_id as moduleId, max(activity.activity_at) as lastActivityAt
        from (
          select module_id, coalesce(last_opened_at, cast(study_date as datetime)) as activity_at
          from lesson_study_days where student_id=:studentId
          union all
          select a.module_id, t.started_at from assessment_attempts t
          join assessments a on a.id=t.assessment_id where t.student_id=:studentId
          union all
          select a.module_id, t.submitted_at from assessment_attempts t
          join assessments a on a.id=t.assessment_id where t.student_id=:studentId
          union all
          select s.ai_learning_module_id, m.created_at from mentor_messages m
          join mentor_sessions s on s.id=m.session_id
          where s.student_id=:studentId and m.sender='STUDENT' and length(trim(m.message))>0
        ) activity
        join ai_learning_modules module on module.id=activity.module_id
        join weeks w on w.id=module.week_id
        where w.subject_id=:subjectId and module.status='APPROVED'
          and module.ai_generation_status='COMPLETED' and activity.activity_at is not null
        group by activity.module_id
        """, nativeQuery = true)
    List<ModuleActivity> findModuleActivity(
            @org.springframework.data.repository.query.Param("subjectId") Long subjectId,
            @org.springframework.data.repository.query.Param("studentId") Long studentId);

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
