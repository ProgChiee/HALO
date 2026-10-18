package com.ptc.halo.repository;

import com.ptc.halo.entity.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.time.*;
import java.util.List;

/** Read-only, bounded dashboard queries. Academic eligibility matches Professor Progress. */
public interface ProfessorDashboardRepository extends Repository<UserEntity,Long> {
 @Query("""
 select count(u) from UserEntity u where u.role=com.ptc.halo.enums.Role.STUDENT
 and u.status=com.ptc.halo.enums.Status.ACTIVE and exists
 (select p.id from StudentProfileEntity p where p.user.id=u.id and exists
 (select s.id from SubjectEntity s where s.professor.user.id=:professorId and s.yearLevel=p.yearLevel))
 """)
 long students(Long professorId);
 String ATTEMPT_SCOPE="""
 a.student.role=com.ptc.halo.enums.Role.STUDENT and a.student.status=com.ptc.halo.enums.Status.ACTIVE
 and a.assessment.module.week.subject.professor.user.id=:professorId
 and exists (select p.id from StudentProfileEntity p where p.user.id=a.student.id
 and p.yearLevel=a.assessment.module.week.subject.yearLevel)
 and a.submittedAt is not null and a.score is not null and a.score between 0 and 100
 """;
 @Query("select count(a) from AssessmentAttemptEntity a where "+ATTEMPT_SCOPE+" and a.passed=true")
 long passed(Long professorId);
 interface Performance { LocalDate getDay(); Double getAverageScore(); Long getAttempts(); }
 @Query("select cast(a.submittedAt as LocalDate) as day, avg(a.score) as averageScore, count(a) as attempts from AssessmentAttemptEntity a where "+ATTEMPT_SCOPE+" and a.submittedAt>=:start and a.submittedAt<:end group by cast(a.submittedAt as LocalDate) order by cast(a.submittedAt as LocalDate)")
 List<Performance> performance(Long professorId,LocalDateTime start,LocalDateTime end);
 String ATTENTION="""
 m.week.subject.professor.user.id=:professorId and (
 (:kind='assessment' and (m.assessmentNeedsReview=true or exists (select q.id from AssessmentEntity q where q.module.id=m.id and q.status=com.ptc.halo.enums.AssessmentStatus.DRAFT))) or
 (:kind='revision' and exists (select r.moduleId from LessonRevisionEntity r where r.moduleId=m.id and r.state in ('DRAFT','REVIEWED'))) or
 (:kind='approval' and m.status=com.ptc.halo.enums.LessonStatus.PENDING and m.aiGenerationStatus=com.ptc.halo.enums.AiGenerationStatus.COMPLETED))
 """;
 interface Lesson { Long getModuleId(); Long getSubjectId(); Long getWeekId(); String getTitle(); }
 @Query("select count(m) from AiLearningModuleEntity m where "+ATTENTION)
 long attentionCount(Long professorId,String kind);
 @Query("select m.id as moduleId,m.week.id as weekId,m.week.subject.id as subjectId,m.week.title as title from AiLearningModuleEntity m where "+ATTENTION+" order by m.id desc")
 List<Lesson> attentionLessons(Long professorId,String kind,Pageable limit);
 @Query("""
 select l from ActivityLogEntity l left join fetch l.user left join fetch l.actingTarget
 where l.activityType in (com.ptc.halo.enums.ActivityType.MODULE,com.ptc.halo.enums.ActivityType.ASSESSMENT)
 and ((l.user.id=:professorId and l.actingTarget is null) or
 (l.actingTarget.id=:professorId and l.actingTargetRole=com.ptc.halo.enums.Role.PROFESSOR))
 order by l.createdAt desc,l.id desc
 """)
 List<ActivityLogEntity> recent(Long professorId,Pageable limit);
}
