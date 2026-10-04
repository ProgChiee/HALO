package com.ptc.halo.repository;

import com.ptc.halo.entity.AssessmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssessmentRepository
        extends JpaRepository<AssessmentEntity, Long> {
    long countByModule_Week_Subject_Professor_User_Id(Long professorId);


    Optional<AssessmentEntity> findByModuleId(Long moduleId);
}