package com.ptc.halo.controller;
import com.ptc.halo.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
@RestController
public class StudentStudyCalendarController extends StudentLessonExceptionHandler {
 private final StudentStudyActivityService activity;private final StudentLessonAccessService access;private final AdminStudentModeService support;private final AdminStudentPreviewService preview;
 public StudentStudyCalendarController(StudentStudyActivityService activity,StudentLessonAccessService access,AdminStudentModeService support,AdminStudentPreviewService preview){this.activity=activity;this.access=access;this.support=support;this.preview=preview;}
 @GetMapping("/api/student/study-calendar") @PreAuthorize("hasRole('STUDENT')")
 public Object calendar(Authentication auth,@RequestParam(required=false) String month){return activity.calendar(access.requireStudent(auth),month);}
 @GetMapping("/api/admin/acting/student/study-calendar") @PreAuthorize("hasRole('ADMIN')")
 public Object support(Authentication auth,@RequestHeader("X-Acting-Session") java.util.UUID session,@RequestParam(required=false) String month){return support.execute(auth,session.toString(),s->activity.calendar(s.getTarget(),month));}
 @GetMapping("/api/admin/preview/student/study-calendar") @PreAuthorize("hasRole('ADMIN')")
 public Object preview(Authentication auth,@RequestHeader("X-Acting-Session") java.util.UUID session,@RequestParam(required=false) String month){return preview.read(auth,session.toString(),s->activity.calendar(s,month));}

 @ExceptionHandler(AdminApiException.class)
 public org.springframework.http.ResponseEntity<?> actingFailure(AdminApiException error){return org.springframework.http.ResponseEntity.status(error.code.status).body(java.util.Map.of("status",error.code.status,"code",error.code.name(),"message",error.code.message));}
 @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
 public org.springframework.http.ResponseEntity<?> missingSession(){return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of("status",400,"code","INVALID_REQUEST","message","An acting session is required."));}
}
