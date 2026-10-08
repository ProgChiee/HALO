package com.ptc.halo.controller;
import com.ptc.halo.service.*;
import com.ptc.halo.dtoRequest.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/acting/professor")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProfessorModeController {
    private final AdminProfessorModeService mode;
    private final ProfessorAcademicService academic;
    private final ProfessorModuleService modules;
    private final ProfessorDashboardService dashboard;
    private final ProfessorStudentService students;
    private final ProfileService profiles;
    public AdminProfessorModeController(AdminProfessorModeService mode, ProfessorAcademicService academic,
            ProfessorModuleService modules, ProfessorDashboardService dashboard, ProfessorStudentService students, ProfileService profiles) {
        this.mode=mode;this.academic=academic;this.modules=modules;this.dashboard=dashboard;this.students=students;this.profiles=profiles;
    }
    @InitBinder public void trimText(org.springframework.web.bind.WebDataBinder binder) {
        binder.registerCustomEditor(String.class,new org.springframework.beans.propertyeditors.StringTrimmerEditor(false));
    }
    @GetMapping("/dashboard")
    public Object dashboard(@RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> dashboard.getDashboard(s.getTarget()));
    }
    @GetMapping("/profile")
    public Object profile(@RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> profiles.getProfessorProfile(s.getTarget()));
    }
    @GetMapping("/subjects")
    public Object subjects(@RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> academic.viewAllSubjects(s.getTarget()));
    }
    @GetMapping("/subjects/{id}")
    public Object subject(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> academic.viewSubjectById(id,s.getTarget()));
    }
    @PostMapping("/subjects")
    public Object createSubject(@Valid @RequestBody SubjectRequest request, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> academic.createSubject(request,s.getTarget(),s));
    }
    @PutMapping("/subjects/{id}")
    public Object updateSubject(@PathVariable @Positive Long id, @Valid @RequestBody SubjectUpdateRequest request, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> academic.updateSubject(id,request,s.getTarget(),s));
    }
    @DeleteMapping("/subjects/{id}")
    public Object deleteSubject(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> { academic.deleteSubject(id,s.getTarget(),s); return "Subject deleted successfully"; });
    }
    @GetMapping("/subjects/{id}/weeks")
    public Object weeks(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> academic.viewAllWeeks(id,s.getTarget()));
    }
    @PostMapping("/subjects/{id}/weeks")
    public Object createWeek(@PathVariable @Positive Long id, @Valid @RequestBody WeekRequest request, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> academic.createWeek(id,request,s.getTarget(),s));
    }
    @GetMapping("/weeks/{id}")
    public Object week(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> academic.viewWeekById(id,s.getTarget()));
    }
    @PutMapping("/weeks/{id}")
    public Object updateWeek(@PathVariable @Positive Long id, @Valid @RequestBody WeekUpdateRequest request, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> academic.updateWeek(id,request,s.getTarget(),s));
    }
    @DeleteMapping("/weeks/{id}")
    public Object deleteWeek(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> { academic.deleteWeek(id,s.getTarget(),s); return "Week deleted successfully"; });
    }
    @GetMapping("/students")
    public Object students(@RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> students.getStudents(s.getTarget()));
    }
    @GetMapping("/students/{id}/progress")
    public Object progress(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> students.getStudentProgress(id,s.getTarget()));
    }
    @GetMapping("/students/{id}/subjects")
    public Object studentSubjects(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> students.getStudentSubjects(id,s.getTarget()));
    }
    @GetMapping("/students/{id}/assessments")
    public Object history(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> students.getStudentAssessmentHistory(id,s.getTarget()));
    }
    @GetMapping("/students/{id}/badges")
    public Object badges(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> students.getStudentBadges(id,s.getTarget()));
    }
    @GetMapping("/students/progress-summaries")
    public Object summaries(@RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) int size, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> { var result=students.getProgressSummaries(s.getTarget(),page,size); return Map.of("content",result.getContent(),"number",result.getNumber(),"size",result.getSize(),"totalElements",result.getTotalElements(),"totalPages",result.getTotalPages(),"first",result.isFirst(),"last",result.isLast(),"badgeScope","INSTITUTION_WIDE"); });
    }
    @GetMapping("/ai-learning-modules/week/{id}")
    public Object moduleByWeek(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> modules.getModuleByWeek(id,s.getTarget(),s));
    }
    @GetMapping("/ai-learning-modules/{id}")
    public Object module(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> modules.getModuleById(id,s.getTarget(),s));
    }
    @PutMapping("/ai-learning-modules/{id}")
    public Object updateModule(@PathVariable @Positive Long id, @Valid @RequestBody AiLearningModuleUpdateRequest request, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> modules.updateModule(id,request,s.getTarget(),s));
    }
    @PutMapping("/ai-learning-modules/{id}/decline")
    public Object decline(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> modules.declineLesson(id,s.getTarget(),s));
    }
    @DeleteMapping("/ai-learning-modules/files/{id}")
    public Object deleteFile(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> modules.deleteFile(id,s.getTarget(),s));
    }
    @PostMapping("/ai-learning-modules/{id}/files")
    public Object upload(@PathVariable @Positive Long id, @RequestParam("file") MultipartFile file, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> modules.uploadFile(id,file,s.getTarget(),s));
    }
    @PostMapping("/ai-learning-modules")
    public Object createModule(@RequestParam @Positive Long weekId,
            @RequestParam(required=false) @Size(max=100000) String lessonText,
            @RequestParam(required=false) @Size(max=255) @org.hibernate.validator.constraints.URL(regexp="https?://.+") String youtubeLink,
            @RequestParam(required=false) @Size(max=10000) String aiNotes,
            @RequestParam(required=false) List<MultipartFile> files, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.execute(authentication,sessionId.toString(),s -> modules.createModule(weekId,lessonText,youtubeLink,aiNotes,files,s.getTarget(),s));
    }
    @PostMapping("/ai-learning-modules/{id}/generate")
    public Object generate(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.generate(authentication,sessionId.toString(),id);
    }
    @PutMapping("/ai-learning-modules/{id}/approve")
    public Object approve(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") java.util.UUID sessionId, Authentication authentication) {
        return mode.approve(authentication,sessionId.toString(),id);
    }
    @ExceptionHandler(AdminApiException.class)
    public ResponseEntity<?> actingFailure(AdminApiException error) {
        return ResponseEntity.status(error.code.status).body(Map.of("status",error.code.status,"code",error.code.name(),"message",error.code.message));
    }
    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    public ResponseEntity<?> missingSession() { return ProfessorErrorResponses.reply(400,"INVALID_REQUEST"); }
}
