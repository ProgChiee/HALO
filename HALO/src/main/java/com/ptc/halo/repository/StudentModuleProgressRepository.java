package com.ptc.halo.repository;

import com.ptc.halo.entity.StudentModuleProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentModuleProgressRepository
        extends JpaRepository<StudentModuleProgressEntity, Long> {

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