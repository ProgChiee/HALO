package com.ptc.halo.repository;

import com.ptc.halo.entity.ProfessorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfessorRepository extends JpaRepository<ProfessorEntity, Long> {

    // SQL may use a case-insensitive collation; compare candidates exactly in Java.
    @org.springframework.data.jpa.repository.Query("select p.professorId from ProfessorEntity p where trim(p.professorId) = :value and (:excludedId is null or p.id <> :excludedId)")
    java.util.List<String> findIdCandidates(@org.springframework.data.repository.query.Param("value") String value,
            @org.springframework.data.repository.query.Param("excludedId") Long excludedId);

    default boolean existsNormalizedProfessorId(String value, Long excludedId) {
        return findIdCandidates(value, excludedId).stream().anyMatch(id -> id.trim().equals(value));
    }

    Optional<ProfessorEntity> findByUserId(Long userId);

    Optional<Object> findByUser_Id(Long id);
}