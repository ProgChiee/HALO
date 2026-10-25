package com.ptc.halo.repository;

import com.ptc.halo.entity.LessonRevisionHistoryEntity;
import org.springframework.data.repository.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

/** Read-only API: publication inserts with EntityManager.persist, never merge/update. */
public interface LessonRevisionHistoryRepository extends Repository<LessonRevisionHistoryEntity,Long> {
    boolean existsByModuleId(Long moduleId);
    boolean existsByModuleIdAndRevisionNumber(Long moduleId,int revisionNumber);
    Optional<LessonRevisionHistoryEntity> findByModuleIdAndRevisionNumber(Long moduleId,int revisionNumber);
    Page<LessonRevisionHistoryEntity> findByModuleIdOrderByRevisionNumberDesc(Long moduleId,Pageable pageable);
}
