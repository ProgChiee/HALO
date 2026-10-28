package com.ptc.halo.repository;
import com.ptc.halo.entity.StudentSubjectEnrollmentEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface StudentSubjectEnrollmentRepository extends JpaRepository<StudentSubjectEnrollmentEntity,Long> {
 boolean existsByStudent_IdAndSubject_Id(Long studentId,Long subjectId);
 @Query("select e.subject.id from StudentSubjectEnrollmentEntity e where e.student.id=:studentId")
 List<Long> subjectIds(@Param("studentId") Long studentId);
}
