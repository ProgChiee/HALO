package com.ptc.halo.controller;
import com.ptc.halo.dtoResponse.AiLearningModuleResponse;
import com.ptc.halo.service.StudentLessonAccessService;
import com.ptc.halo.service.StudentLessonService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('STUDENT')")
@RequestMapping("/api/student/ai-learning-modules")
public class StudentAiLearningController {
    private final StudentLessonService lessons;
    private final StudentLessonAccessService access;
    public StudentAiLearningController(StudentLessonService lessons, StudentLessonAccessService access) {
        this.lessons=lessons; this.access=access;
    }
    @GetMapping("/week/{weekId}")
    public ResponseEntity<AiLearningModuleResponse> getApprovedLesson(@PathVariable("weekId") Long weekId, Authentication authentication) {
        return ResponseEntity.ok(lessons.byWeek(weekId,access.requireStudent(authentication)));
    }
}
