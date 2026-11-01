package com.ptc.halo.repository;

import com.ptc.halo.entity.SubjectEntity;
import com.ptc.halo.enums.YearLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubjectRepository
        extends JpaRepository<SubjectEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from SubjectEntity s where s.id=:id and s.professor.user.id=:owner")
    Optional<SubjectEntity> findCoverForUpdate(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("owner") Long owner);
    long countByProfessor_User_Id(Long professorId);

    List<SubjectEntity> findByProfessor_User_Id(Long userId);
    Optional<SubjectEntity> findByIdAndProfessor_User_Id(Long id, Long userId);

    Optional<SubjectEntity> findBySubjectCode(
            String subjectCode
    );

    Optional<SubjectEntity> findBySubjectName(
            String subjectName
    );

    List<SubjectEntity> findByYearLevel(
            YearLevel yearLevel
    );
}
