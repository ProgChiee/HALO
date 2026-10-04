package com.ptc.halo.repository;

import com.ptc.halo.entity.AiLearningFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiLearningFileRepository
        extends JpaRepository<AiLearningFileEntity, Long> {
    java.util.Optional<AiLearningFileEntity> findByIdAndModule_Week_Subject_Professor_User_Id(Long id, Long userId);
}