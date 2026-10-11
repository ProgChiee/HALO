package com.ptc.halo.controller;
import com.ptc.halo.service.*;
import com.ptc.halo.enums.ActivityType;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
@RestController
public class LessonStudyController extends StudentLessonExceptionHandler {
 private final LessonStudyService study;
 private final StudentLessonAccessService access;
 private final AdminStudentModeService support;
 private final ActivityLogService audit;
 public LessonStudyController(LessonStudyService study,StudentLessonAccessService access,AdminStudentModeService support,ActivityLogService audit){this.study=study;this.access=access;this.support=support;this.audit=audit;}
 @PostMapping("/api/student/ai-learning-modules/week/{weekId}/study")
 @PreAuthorize("hasRole('STUDENT')")
 public ResponseEntity<Void> record(@PathVariable Long weekId,Authentication auth){study.record(weekId,access.requireStudent(auth));return ResponseEntity.noContent().build();}
 @PostMapping("/api/admin/acting/student/ai-learning-modules/week/{weekId}/study")
 @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<Void> support(@PathVariable Long weekId,Authentication auth,@RequestHeader("X-Acting-Session") java.util.UUID session){
  support.execute(auth,session.toString(),s->{var moduleId=study.record(weekId,s.getTarget());audit.createActingLog(s,ActivityType.MODULE,"Studied lesson module ID "+moduleId);return null;});
  return ResponseEntity.noContent().build();
 }
 // Deliberately no preview route. Preview IDs cannot authorize real support writes.
}
