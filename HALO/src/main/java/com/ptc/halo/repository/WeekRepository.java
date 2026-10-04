package com.ptc.halo.repository;

import com.ptc.halo.entity.WeekEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeekRepository
        extends JpaRepository<WeekEntity, Long> {
    java.util.Optional<WeekEntity> findByIdAndSubject_Professor_User_Id(Long id, Long userId);

    List<WeekEntity> findBySubject_Id(Long subjectId);

    List<WeekEntity>
    findBySubject_IdOrderByWeekNumberAsc(Long subjectId);
}
