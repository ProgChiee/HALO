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
    @org.springframework.data.jpa.repository.Query("select s from SubjectEntity s where s.archivedAt is null and s.professor.user.status = com.ptc.halo.enums.Status.ACTIVE and s.yearLevel=:year")
    List<SubjectEntity> browsableSubjects(@org.springframework.data.repository.query.Param("year") YearLevel year);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from SubjectEntity s join fetch s.professor p join fetch p.user u where s.id=:id and s.archivedAt is null and u.status=com.ptc.halo.enums.Status.ACTIVE")
    Optional<SubjectEntity> lockForEnrollment(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("select count(e) from StudentSubjectEnrollmentEntity e where e.subject.id=:id")
    long enrollmentCount(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("""
        select count(s) > 0 from SubjectEntity s where s.id=:id and s.yearLevel=:year
            and s.archivedAt is null and s.professor.user.status=com.ptc.halo.enums.Status.ACTIVE
            and exists (select m.id from AiLearningModuleEntity m where m.week.subject=s
                        and m.status=com.ptc.halo.enums.LessonStatus.APPROVED
                        and m.aiGenerationStatus=com.ptc.halo.enums.AiGenerationStatus.COMPLETED)
        """)
    boolean browsableCover(long id, YearLevel year);

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
