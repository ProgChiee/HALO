package com.ptc.halo.repository;

import com.ptc.halo.entity.WeekEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeekRepository
        extends JpaRepository<WeekEntity, Long> {
    java.util.Optional<WeekEntity> findByIdAndSubject_Professor_User_Id(Long id, Long userId);

    boolean existsBySubject_IdAndWeekNumber(Long subjectId, Integer weekNumber);
    boolean existsBySubject_IdAndWeekNumberAndIdNot(Long subjectId, Integer weekNumber, Long id);

    boolean existsBySubject_Id(Long subjectId);

    List<WeekEntity> findBySubject_Id(Long subjectId);

    List<WeekEntity>
    findBySubject_IdOrderByWeekNumberAsc(Long subjectId);
}
