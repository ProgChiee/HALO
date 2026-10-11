package com.ptc.halo.controller;
import com.ptc.halo.service.*;
import com.ptc.halo.dtoRequest.*;
import com.ptc.halo.dtoResponse.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/preview/student")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStudentPreviewController {
    private final AdminStudentPreviewService mode;
    private final StudentDashboardService dashboard;
    private final StudentSubjectService subjects;
    private final StudentLearningProgressionService progression;
    private final StudentLessonService lessons;
    private final StudentProgressService progress;
    private final BadgeService badges;
    private final AssessmentService assessments;
    private final ProfileService profiles;
    public AdminStudentPreviewController(AdminStudentPreviewService mode, StudentDashboardService dashboard,
            StudentSubjectService subjects, StudentLearningProgressionService progression, StudentLessonService lessons,
            StudentProgressService progress, BadgeService badges, AssessmentService assessments, ProfileService profiles) {
        this.mode=mode; this.dashboard=dashboard; this.subjects=subjects; this.progression=progression;
        this.lessons=lessons; this.progress=progress; this.badges=badges; this.assessments=assessments;
        this.profiles=profiles;
    }
    @GetMapping("/dashboard")
    public Object dashboard(@RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.read(authentication,actingSessionId.toString(),s -> dashboard.getDashboard(s));
    }
    @GetMapping("/subjects")
    public Object subjects(@RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.read(authentication,actingSessionId.toString(),s -> subjects.getStudentSubjects(s));
    }
    @GetMapping("/subjects/{id}/weeks")
    public Object weeks(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.read(authentication,actingSessionId.toString(),s -> progression.getWeekAccess(id,s));
    }
    @GetMapping("/ai-learning-modules/week/{id}")
    public Object lesson(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.read(authentication,actingSessionId.toString(),s -> lessons.byWeek(id,s));
    }
    @GetMapping("/progress")
    public Object progress(@RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.read(authentication,actingSessionId.toString(),s -> progress.getProgress(s));
    }
    @GetMapping("/badges")
    public Object badges(@RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.read(authentication,actingSessionId.toString(),s -> badges.getStudentBadges(s));
    }
    @GetMapping("/profile")
    public Object profile(@RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.read(authentication,actingSessionId.toString(),s -> profiles.getStudentProfile(s));
    }
    @GetMapping("/assessment/{id}")
    public Object assessment(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.read(authentication,actingSessionId.toString(),s -> assessments.getAssessment(id,s));
    }
    @GetMapping("/assessment/status/{id}")
    public Object status(@PathVariable @Positive Long id,@RequestHeader("X-Acting-Session") UUID session,Authentication auth) { return mode.status(auth,session.toString(),id); }
    @GetMapping("/assessment/attempts/{id}")
    public Object history(@PathVariable @Positive Long id,@RequestHeader("X-Acting-Session") UUID session,Authentication auth) { return mode.history(auth,session.toString(),id); }
    @GetMapping("/assessment/result/{id}")
    public Object result(@PathVariable @Positive Long id,@RequestHeader("X-Acting-Session") UUID session,Authentication auth) { return mode.result(auth,session.toString(),id); }
    @PostMapping("/assessment/start/{id}")
    public Object start(@PathVariable @Positive Long id,@RequestHeader("X-Acting-Session") UUID session,Authentication auth) { return mode.start(auth,session.toString(),id); }
    @PostMapping("/mentor/open/{id}")
    public Object open(@PathVariable @Positive Long id,@RequestHeader("X-Acting-Session") UUID session,Authentication auth) { return mode.open(auth,session.toString(),id); }
    public record StartPreview(@jakarta.validation.constraints.NotNull @Positive Long targetUserId) {}
    @PostMapping("/sessions")
    public Object create(Authentication auth,@Valid @RequestBody StartPreview request) { return mode.create(auth,request.targetUserId()); }
    @GetMapping("/sessions/{id}")
    public Object validate(Authentication auth,@PathVariable UUID id) { return mode.validate(auth,id.toString()); }
    @DeleteMapping("/sessions/{id}")
    public void revoke(Authentication auth,@PathVariable UUID id) { mode.revoke(auth,id.toString()); }
    @PostMapping("/assessment/submit/{id}")
    public Object submit(@PathVariable @Positive Long id,@Valid @RequestBody StudentAnswerRequest request,@RequestHeader("X-Acting-Session") UUID session,Authentication auth) { return mode.submit(auth,session.toString(),id,request); }
    @PostMapping("/mentor/message/{id}")
    public Object send(@PathVariable @Positive Long id,@RequestBody MentorMessageRequest request,@RequestHeader("X-Acting-Session") UUID session,Authentication auth) { return mode.send(auth,session.toString(),id,request.getMessage(),request.getRequestId()); }
    @GetMapping("/mentor/session/{id}")
    public Object conversation(@PathVariable @Positive Long id,@RequestParam(required=false) @Positive Long beforeId,@RequestHeader("X-Acting-Session") UUID session,Authentication auth) { return mode.conversation(auth,session.toString(),id,beforeId); }
    @GetMapping("/mentor/message/{id}/request/{requestId}")
    public Object exchange(@PathVariable @Positive Long id,@PathVariable String requestId,@RequestHeader("X-Acting-Session") UUID session,Authentication auth) { return mode.exchange(auth,session.toString(),id,requestId); }
}
