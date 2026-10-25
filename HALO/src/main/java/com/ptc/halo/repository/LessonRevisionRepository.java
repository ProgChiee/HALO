package com.ptc.halo.repository;
import com.ptc.halo.entity.LessonRevisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LessonRevisionRepository extends JpaRepository<LessonRevisionEntity,Long> {
 // Caller holds the module write lock. Quiz approval changes only the review flag,
 // so carry forward a matching baseline without changing the draft-content edit token.
 // Never rebase an already-stale draft or a published historical revision.
 @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true)
 @org.springframework.data.jpa.repository.Query("update LessonRevisionEntity r set r.baseVersion = :nextVersion where r.moduleId = :moduleId and r.baseVersion = :previousVersion and r.state in ('DRAFT', 'REVIEWED')")
 int advanceBaselineAfterQuizApproval(@org.springframework.data.repository.query.Param("moduleId") Long moduleId,
  @org.springframework.data.repository.query.Param("previousVersion") Long previousVersion,
  @org.springframework.data.repository.query.Param("nextVersion") Long nextVersion);
}
