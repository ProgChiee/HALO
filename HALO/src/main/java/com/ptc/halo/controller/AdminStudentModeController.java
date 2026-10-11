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
@RequestMapping("/api/admin/acting/student")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStudentModeController {
    private final AdminStudentModeService mode;
    private final StudentDashboardService dashboard;
    private final StudentSubjectService subjects;
    private final StudentLearningProgressionService progression;
    private final StudentLessonService lessons;
    private final StudentProgressService progress;
    private final BadgeService badges;
    private final AssessmentService assessments;
    private final MentorService mentor;
    private final ProfileService profiles;
    public AdminStudentModeController(AdminStudentModeService mode, StudentDashboardService dashboard,
            StudentSubjectService subjects, StudentLearningProgressionService progression, StudentLessonService lessons,
            StudentProgressService progress, BadgeService badges, AssessmentService assessments, MentorService mentor, ProfileService profiles) {
        this.mode=mode; this.dashboard=dashboard; this.subjects=subjects; this.progression=progression;
        this.lessons=lessons; this.progress=progress; this.badges=badges; this.assessments=assessments;
        this.mentor=mentor; this.profiles=profiles;
    }
    @GetMapping("/dashboard")
    public Object dashboard(@RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.execute(authentication,actingSessionId.toString(),s -> dashboard.getDashboard(s.getTarget()));
    }
    @GetMapping("/subjects")
    public Object subjects(@RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.execute(authentication,actingSessionId.toString(),s -> subjects.getStudentSubjects(s.getTarget()));
    }
    @GetMapping("/subjects/{id}/weeks")
    public Object weeks(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.execute(authentication,actingSessionId.toString(),s -> progression.getWeekAccess(id,s.getTarget()));
    }
    @GetMapping("/ai-learning-modules/week/{id}")
    public Object lesson(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.execute(authentication,actingSessionId.toString(),s -> lessons.byWeek(id,s.getTarget()));
    }
    @GetMapping("/progress")
    public Object progress(@RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.execute(authentication,actingSessionId.toString(),s -> progress.getProgress(s.getTarget()));
    }
    @GetMapping("/badges")
    public Object badges(@RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.execute(authentication,actingSessionId.toString(),s -> badges.getStudentBadges(s.getTarget()));
    }
    @GetMapping("/profile")
    public Object profile(@RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.execute(authentication,actingSessionId.toString(),s -> profiles.getStudentProfile(s.getTarget()));
    }
    @GetMapping("/assessment/{id}")
    public Object assessment(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.execute(authentication,actingSessionId.toString(),s -> assessments.getAssessment(id,s.getTarget()));
    }
    @GetMapping("/assessment/attempts/{id}")
    public Object history(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.execute(authentication,actingSessionId.toString(),s -> assessments.getAttemptHistory(id,s.getTarget()));
    }
    @GetMapping("/assessment/result/{id}")
    public Object result(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.execute(authentication,actingSessionId.toString(),s -> assessments.getAttemptResult(id,s.getTarget()));
    }
    @GetMapping("/assessment/status/{id}")
    public Object status(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.execute(authentication,actingSessionId.toString(),s -> assessments.getAssessmentStatus(id,s.getTarget()));
    }
    @PostMapping("/assessment/start/{id}")
    public Object start(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.execute(authentication,actingSessionId.toString(),s -> {
            var attempt=assessments.startAttempt(id,s.getTarget(),s);
            var response=new AssessmentAttemptResponse();
            response.setAttemptId(attempt.getId()); response.setAssessmentId(attempt.getAssessment().getId()); response.setStartedAt(attempt.getStartedAt());
            return response;
        });
    }
    @PostMapping("/assessment/submit/{id}")
    public Object submit(@PathVariable @Positive Long id, @Valid @RequestBody StudentAnswerRequest request,
            @RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.execute(authentication,actingSessionId.toString(),s -> {
            var attempt=assessments.submitAttempt(id,s.getTarget(),request,s);
            var response=new AssessmentResultResponse();
            response.setAttemptId(attempt.getId()); response.setScore(attempt.getScore()); response.setPassed(attempt.getPassed());
            return response;
        });
    }
    @PostMapping("/mentor/open/{id}")
    public Object openMentor(@PathVariable @Positive Long id, @RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.openMentor(authentication,actingSessionId.toString(),id);
    }
    @PostMapping("/mentor/message/{id}")
    public Object send(@PathVariable @Positive Long id, @RequestBody MentorMessageRequest request,
            @RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.sendMessage(authentication,actingSessionId.toString(),id,request.getMessage(),request.getRequestId());
    }
    @GetMapping("/mentor/session/{id}")
    public Object conversation(@PathVariable @Positive Long id, @RequestParam(required=false) @Positive Long beforeId,
            @RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.execute(authentication,actingSessionId.toString(),s -> mentor.getConversation(id,s.getTarget(),beforeId));
    }
    @GetMapping("/mentor/message/{id}/request/{requestId}")
    public Object exchange(@PathVariable @Positive Long id, @PathVariable String requestId,
            @RequestHeader("X-Acting-Session") UUID actingSessionId, Authentication authentication) {
        return mode.execute(authentication,actingSessionId.toString(),s -> mentor.getExchange(id,s.getTarget(),requestId));
    }
}
