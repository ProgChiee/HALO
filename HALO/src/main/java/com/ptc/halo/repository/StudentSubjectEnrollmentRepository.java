package com.ptc.halo.repository;
import com.ptc.halo.entity.StudentSubjectEnrollmentEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface StudentSubjectEnrollmentRepository extends JpaRepository<StudentSubjectEnrollmentEntity,Long> {
 boolean existsByStudent_IdAndSubject_IdAndActiveTrue(Long studentId,Long subjectId);
 @Query("select e.subject.id from StudentSubjectEnrollmentEntity e where e.student.id=:studentId and e.active=true")
 List<Long> subjectIds(@Param("studentId") Long studentId);
 @Query("select s from StudentSubjectEnrollmentEntity e left join e.subject s where e.student.id=:studentId and e.active=true order by e.id")
 List<com.ptc.halo.entity.SubjectEntity> findActiveSubjects(@Param("studentId") Long studentId);
 // Current locking reads also avoid stale repeatable-read snapshots after waiting for a writer.
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select e from StudentSubjectEnrollmentEntity e where e.student.id=:studentId and e.subject.id=:subjectId")
 java.util.Optional<StudentSubjectEnrollmentEntity> findForUpdate(@Param("studentId") Long studentId,@Param("subjectId") Long subjectId);
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select e from StudentSubjectEnrollmentEntity e where e.student.id=:studentId and e.subject.id=:subjectId and e.active=true")
 java.util.Optional<StudentSubjectEnrollmentEntity> findActiveForUpdate(@Param("studentId") Long studentId,@Param("subjectId") Long subjectId);
}
