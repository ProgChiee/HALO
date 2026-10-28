package com.ptc.halo.controller;
import com.ptc.halo.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
@RestController
public class StudentEnrollmentController {
 private final StudentEnrollmentService enrollment;
 private final StudentCurriculumService curriculum;
 private final StudentLessonAccessService access;
 private final AdminStudentPreviewService preview;
 private final AdminStudentModeService support;
 public StudentEnrollmentController(StudentEnrollmentService enrollment,StudentLessonAccessService access,AdminStudentPreviewService preview,AdminStudentModeService support,StudentCurriculumService curriculum){this.curriculum=curriculum;this.enrollment=enrollment;this.access=access;this.preview=preview;this.support=support;}
 @GetMapping("/api/student/subjects/my") @PreAuthorize("hasRole('STUDENT')")
 public Object mine(Authentication auth){return enrollment.list(access.requireStudent(auth),true);}
 @GetMapping("/api/student/subjects/browse") @PreAuthorize("hasRole('STUDENT')")
 public Object browse(Authentication auth){return enrollment.list(access.requireStudent(auth),false);}
 @PostMapping("/api/student/subjects/{subjectId}/enrollment") @PreAuthorize("hasRole('STUDENT')")
 public Object enroll(Authentication auth,@PathVariable Long subjectId,@RequestHeader(value="X-Acting-Session",required=false) String acting){
  if(acting!=null)throw new org.springframework.security.access.AccessDeniedException("Enrollment is read-only in acting modes");
  enrollment.enroll(auth,subjectId);return java.util.Map.of("enrolled",true);
 }
 @GetMapping("/api/admin/preview/student/subjects/my") @PreAuthorize("hasRole('ADMIN')")
 public Object previewMine(Authentication auth,@RequestHeader("X-Acting-Session") java.util.UUID session){return preview.read(auth,session.toString(),s->enrollment.list(s,true));}
 @GetMapping("/api/admin/preview/student/subjects/browse") @PreAuthorize("hasRole('ADMIN')")
 public Object previewBrowse(Authentication auth,@RequestHeader("X-Acting-Session") java.util.UUID session){return preview.read(auth,session.toString(),s->enrollment.list(s,false));}
 @GetMapping("/api/admin/acting/student/subjects/my") @PreAuthorize("hasRole('ADMIN')")
 public Object supportMine(Authentication auth,@RequestHeader("X-Acting-Session") java.util.UUID session){return support.execute(auth,session.toString(),s->enrollment.list(s.getTarget(),true));}
 @GetMapping("/api/admin/acting/student/subjects/browse") @PreAuthorize("hasRole('ADMIN')")
 public Object supportBrowse(Authentication auth,@RequestHeader("X-Acting-Session") java.util.UUID session){return support.execute(auth,session.toString(),s->enrollment.list(s.getTarget(),false));}
 @GetMapping("/api/student/subjects/{subjectId}/curriculum") @PreAuthorize("hasRole('STUDENT')")
 public com.ptc.halo.dtoResponse.StudentCurriculumResponse curriculum(Authentication auth,@PathVariable Long subjectId){return curriculum.preview(access.requireStudent(auth),subjectId);}
 @GetMapping("/api/admin/preview/student/subjects/{subjectId}/curriculum") @PreAuthorize("hasRole('ADMIN')")
 public Object previewCurriculum(Authentication auth,@PathVariable Long subjectId,@RequestHeader("X-Acting-Session") java.util.UUID session){return preview.read(auth,session.toString(),s->curriculum.preview(s,subjectId));}
 @GetMapping("/api/admin/acting/student/subjects/{subjectId}/curriculum") @PreAuthorize("hasRole('ADMIN')")
 public Object supportCurriculum(Authentication auth,@PathVariable Long subjectId,@RequestHeader("X-Acting-Session") java.util.UUID session){return support.execute(auth,session.toString(),s->curriculum.preview(s.getTarget(),subjectId));}
 @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
 public org.springframework.http.ResponseEntity<?> rejected(org.springframework.web.server.ResponseStatusException e){return org.springframework.http.ResponseEntity.status(e.getStatusCode()).body(java.util.Map.of("code",e.getReason()==null?"REQUEST_REJECTED":e.getReason()));}
 @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
 public org.springframework.http.ResponseEntity<?> forbidden(){return org.springframework.http.ResponseEntity.status(403).body(java.util.Map.of("code","ACCESS_DENIED"));}
}
