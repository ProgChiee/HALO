package com.ptc.halo.repository;
import com.ptc.halo.entity.LessonStudyDayEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
public interface LessonStudyDayRepository extends JpaRepository<LessonStudyDayEntity,Long> {
 boolean existsByStudentIdAndModuleIdAndStudyDate(Long studentId,Long moduleId,LocalDate studyDate);
}
