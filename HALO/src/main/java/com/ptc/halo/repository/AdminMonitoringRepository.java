package com.ptc.halo.repository;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.*;
import java.util.List;
import java.time.LocalDateTime;

public interface AdminMonitoringRepository extends Repository<UserEntity, Long> {
 String PROFESSORS = """
 select u.id as userId, p.professorId as professorId, u.name as name, u.email as email, u.status as status,
 (select count(l.id) from ActivityLogEntity l where l.user.id=u.id and l.activityType=com.ptc.halo.enums.ActivityType.MODULE) as moduleActivities,
 (select max(l.createdAt) from ActivityLogEntity l where l.user.id=u.id) as lastActivity
 from ProfessorEntity p join p.user u where u.role=com.ptc.halo.enums.Role.PROFESSOR
 """;
 String STUDENTS = """
 select u.id as userId, p.studentId as studentId, u.name as name, u.email as email, p.section as section, p.yearLevel as yearLevel, u.status as status,
 (select count(m.id) from StudentModuleProgressEntity m where m.student.id=u.id and m.completed=true) as completedModules,
 (select count(a.id) from AssessmentAttemptEntity a where a.student.id=u.id and a.passed=true) as passedAssessments,
 (select count(b.id) from StudentBadgeEntity b where b.student.id=u.id) as totalBadges,
 (select a.score from AssessmentAttemptEntity a where a.student.id=u.id and a.submittedAt is not null
 and not exists (select newer.id from AssessmentAttemptEntity newer where newer.student.id=u.id and
 (newer.submittedAt>a.submittedAt or (newer.submittedAt=a.submittedAt and newer.id>a.id)))) as latestAssessmentScore
 from StudentProfileEntity p join p.user u where u.role=com.ptc.halo.enums.Role.STUDENT
 """;
 interface ProfessorRow {
  Long getUserId(); String getProfessorId(); String getName(); String getEmail(); Status getStatus();
  Long getModuleActivities(); LocalDateTime getLastActivity();
 }
 interface StudentRow {
  Long getUserId(); String getStudentId(); String getName(); String getEmail(); String getSection(); YearLevel getYearLevel(); Status getStatus();
  Long getCompletedModules(); Long getPassedAssessments(); Long getTotalBadges(); Integer getLatestAssessmentScore();
 }
 interface ProfessorTotals { Long getTotal(); Long getActive(); Long getModuleActivities(); }
 interface StudentTotals { Long getTotal(); Long getScored(); Double getAverageScore(); Long getPassedAssessments(); }
 @Query(value=PROFESSORS + " order by moduleActivities desc, userId asc", countQuery="select count(p) from ProfessorEntity p where p.user.role=com.ptc.halo.enums.Role.PROFESSOR")
 Page<ProfessorRow> professors(Pageable pageable);
 @Query(PROFESSORS + " order by lastActivity desc, userId asc")
 List<ProfessorRow> recentProfessors(Pageable pageable);
 @Query("select count(d.userId) as total, sum(case when d.status=com.ptc.halo.enums.Status.ACTIVE then 1L else 0L end) as active, sum(d.moduleActivities) as moduleActivities from (" + PROFESSORS + ") d")
 ProfessorTotals professorTotals();
 @Query(value=STUDENTS + " order by latestAssessmentScore desc, userId asc", countQuery="select count(p) from StudentProfileEntity p where p.user.role=com.ptc.halo.enums.Role.STUDENT")
 Page<StudentRow> students(Pageable pageable);
 @Query("select d.userId as userId, d.studentId as studentId, d.name as name, d.email as email, d.section as section, d.yearLevel as yearLevel, d.status as status, d.completedModules as completedModules, d.passedAssessments as passedAssessments, d.totalBadges as totalBadges, d.latestAssessmentScore as latestAssessmentScore from (" + STUDENTS + ") d where d.latestAssessmentScore is not null order by d.latestAssessmentScore asc, d.userId asc")
 List<StudentRow> lowestStudents(Pageable pageable);
 @Query("select count(d.userId) as total, count(d.latestAssessmentScore) as scored, avg(d.latestAssessmentScore) as averageScore, sum(d.passedAssessments) as passedAssessments from (" + STUDENTS + ") d")
 StudentTotals studentTotals();
}
