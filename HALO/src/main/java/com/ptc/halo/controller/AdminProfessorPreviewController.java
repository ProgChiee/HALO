package com.ptc.halo.controller;
import com.ptc.halo.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.*;
import java.util.Map;
@RestController
@RequestMapping("/api/admin/preview/professor")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProfessorPreviewController {
 private final AdminProfessorPreviewService mode;
 private final ProfessorAcademicService academic;
 private final ProfessorModuleService modules;
 private final ProfessorDashboardService dashboard;
 private final ProfessorStudentService students;
 private final ProfileService profiles;
 private final ProfessorEngagementService engagement;
 private final ProfilePhotoService photos;
 public AdminProfessorPreviewController(AdminProfessorPreviewService mode,ProfessorAcademicService academic,ProfessorModuleService modules,ProfessorDashboardService dashboard,ProfessorStudentService students,ProfileService profiles,ProfessorEngagementService engagement,ProfilePhotoService photos){this.mode=mode;this.academic=academic;this.modules=modules;this.dashboard=dashboard;this.students=students;this.profiles=profiles;this.engagement=engagement;this.photos=photos;}
 public record Target(@NotNull @Positive Long targetUserId){}
 @PostMapping("/sessions") public Object create(Authentication auth,@Valid @RequestBody Target target){return mode.create(auth,target.targetUserId());}
 @GetMapping("/sessions/{id}") public Object validate(Authentication auth,@PathVariable java.util.UUID id){return mode.validate(auth,id.toString());}
 @DeleteMapping("/sessions/{id}") public ResponseEntity<Void> revoke(Authentication auth,@PathVariable java.util.UUID id){mode.revoke(auth,id.toString());return ResponseEntity.noContent().build();}
    @GetMapping("/dashboard")
    public Object dashboard(@RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.read(authentication,sessionId.toString(),s -> dashboard.getDashboard(s.getTarget()));
    }
    @GetMapping("/profile")
    public Object profile(@RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.read(authentication,sessionId.toString(),s -> profiles.getProfessorProfile(s.getTarget()));
    }
    @GetMapping("/subjects")
    public Object subjects(@RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.read(authentication,sessionId.toString(),s -> academic.viewAllSubjects(s.getTarget()));
    }
    @GetMapping("/subjects/{id}")
    public Object subject(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.read(authentication,sessionId.toString(),s -> academic.viewSubjectById(id,s.getTarget()));
    }
    @GetMapping("/subjects/{id}/weeks")
    public Object weeks(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.read(authentication,sessionId.toString(),s -> academic.viewAllWeeks(id,s.getTarget()));
    }
    @GetMapping("/weeks/{id}")
    public Object week(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.read(authentication,sessionId.toString(),s -> academic.viewWeekById(id,s.getTarget()));
    }
    @GetMapping("/students")
    public Object students(@RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.read(authentication,sessionId.toString(),s -> students.getStudents(s.getTarget()));
    }
    @GetMapping("/students/{id}/progress")
    public Object progress(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.read(authentication,sessionId.toString(),s -> students.getStudentProgress(id,s.getTarget()));
    }
    @GetMapping("/students/{id}/subjects")
    public Object studentSubjects(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.read(authentication,sessionId.toString(),s -> students.getStudentSubjects(id,s.getTarget()));
    }
    @GetMapping("/students/{id}/assessments")
    public Object history(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.read(authentication,sessionId.toString(),s -> students.getStudentAssessmentHistory(id,s.getTarget()));
    }
    @GetMapping("/students/{id}/badges")
    public Object badges(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.read(authentication,sessionId.toString(),s -> students.getStudentBadges(id,s.getTarget()));
    }
    @GetMapping("/students/progress-summaries")
    public Object summaries(@RequestParam(defaultValue="CURRENT") com.ptc.halo.enums.ProfessorStudentView view, @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) int size, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.read(authentication,sessionId.toString(),s -> { var result=students.getProgressSummaries(s.getTarget(),page,size,view); return Map.of("content",result.getContent(),"number",result.getNumber(),"size",result.getSize(),"totalElements",result.getTotalElements(),"totalPages",result.getTotalPages(),"first",result.isFirst(),"last",result.isLast(),"badgeScope","INSTITUTION_WIDE"); });
    }
    @GetMapping("/ai-learning-modules/week/{id}")
    public Object moduleByWeek(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.read(authentication,sessionId.toString(),s -> modules.getModuleByWeek(id,s.getTarget(),s));
    }
    @GetMapping("/ai-learning-modules/{id}")
    public Object module(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.read(authentication,sessionId.toString(),s -> modules.getModuleById(id,s.getTarget(),s));
    }
 @GetMapping("/students/engagement") public Object engagement(Authentication auth,@RequestHeader("X-Acting-Session") java.util.UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="CURRENT") com.ptc.halo.enums.ProfessorStudentView view, @RequestParam(defaultValue="20") int size){return mode.read(auth,id.toString(),s->engagement.get(s.getTarget(),page,size,view));}
 @GetMapping("/students/{student}/engagement/modules") public Object moduleEngagement(Authentication auth,@RequestHeader("X-Acting-Session") java.util.UUID id,@PathVariable Long student,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="CURRENT") com.ptc.halo.enums.ProfessorStudentView view, @RequestParam(defaultValue="20") int size){return mode.read(auth,id.toString(),s->engagement.modules(s.getTarget(),student,page,size,view));}
 @GetMapping("/profile/photo") public Object photo(Authentication auth,@RequestHeader("X-Acting-Session") java.util.UUID id){return mode.read(auth,id.toString(),s->{try{var image=photos.read(s.getTarget().getId());return image==null?ResponseEntity.noContent().build():ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(image);}catch(java.io.IOException e){throw new org.springframework.web.server.ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"PHOTO_UNAVAILABLE");}});}
 @RequestMapping(value="/**",method={RequestMethod.POST,RequestMethod.PUT,RequestMethod.PATCH,RequestMethod.DELETE}) public Object readOnly(Authentication auth,@RequestHeader("X-Acting-Session") java.util.UUID id){return mode.read(auth,id.toString(),s->{throw new AdminApiException(AdminApiException.Code.PREVIEW_READ_ONLY);});}
 @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class) public ResponseEntity<?> missing(){return ResponseEntity.badRequest().body(Map.of("status",400,"code","ACTING_SESSION_REQUIRED","message","Start Professor Preview before viewing this content."));}
 @ExceptionHandler(AdminApiException.class) public ResponseEntity<?> failure(AdminApiException e){return ResponseEntity.status(e.code.status).body(Map.of("status",e.code.status,"code",e.code.name(),"message",e.code.message));}
}
