package com.ptc.halo.controller;

import com.ptc.halo.service.*;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.security.AuthenticatedAccount;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
public class LessonAttachmentController {
    private final LessonAttachmentService files;
    private final StudentLessonAccessService students;
    private final UserRepository users;
    private final AdminProfessorModeService professorMode;
    private final AdminProfessorPreviewService professorPreview;
    private final AdminStudentModeService studentMode;
    private final AdminStudentPreviewService studentPreview;
    public LessonAttachmentController(LessonAttachmentService files,StudentLessonAccessService students,UserRepository users,
        AdminProfessorModeService professorMode,AdminProfessorPreviewService professorPreview,AdminStudentModeService studentMode,AdminStudentPreviewService studentPreview) {
        this.files=files;this.students=students;this.users=users;this.professorMode=professorMode;this.professorPreview=professorPreview;this.studentMode=studentMode;this.studentPreview=studentPreview;
    }
    private ResponseEntity<byte[]> response(LessonAttachmentService.Content content,boolean download) {
        var disposition=(download?ContentDisposition.attachment():ContentDisposition.inline()).filename(content.name(),java.nio.charset.StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.type())).contentLength(content.bytes().length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .header("Content-Security-Policy","sandbox").header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString()).body(content.bytes());
    }
    @GetMapping("/api/student/ai-learning-modules/files/{id}/content") @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<byte[]> student(Authentication auth,@PathVariable Long id,@RequestParam(defaultValue="false") boolean download) {
        return response(files.student(id,students.requireStudent(auth)),download);
    }
    @GetMapping("/api/professor/ai-learning-modules/files/{id}/content") @PreAuthorize("hasRole('PROFESSOR')")
    public ResponseEntity<byte[]> professor(Authentication auth,@PathVariable Long id,@RequestParam(defaultValue="false") boolean download) {
        return response(files.professor(id,AuthenticatedAccount.find(users,auth).orElseThrow()),download);
    }
    @GetMapping("/api/admin/acting/student/ai-learning-modules/files/{id}/content") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> support(Authentication auth,@RequestHeader("X-Acting-Session") UUID session,@PathVariable Long id,@RequestParam(defaultValue="false") boolean download) {
        return studentMode.execute(auth,session.toString(),s->response(files.student(id,s.getTarget()),download));
    }
    @GetMapping("/api/admin/preview/student/ai-learning-modules/files/{id}/content") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> previewStudent(Authentication auth,@RequestHeader("X-Acting-Session") UUID session,@PathVariable Long id,@RequestParam(defaultValue="false") boolean download) {
        return studentPreview.read(auth,session.toString(),s->response(files.student(id,s),download));
    }
    @GetMapping("/api/admin/acting/professor/ai-learning-modules/files/{id}/content") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> actingProfessor(Authentication auth,@RequestHeader("X-Acting-Session") UUID session,@PathVariable Long id,@RequestParam(defaultValue="false") boolean download) {
        return professorMode.execute(auth,session.toString(),s->response(files.professor(id,s.getTarget()),download));
    }
    @GetMapping("/api/admin/preview/professor/ai-learning-modules/files/{id}/content") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> previewProfessor(Authentication auth,@RequestHeader("X-Acting-Session") UUID session,@PathVariable Long id,@RequestParam(defaultValue="false") boolean download) {
        return professorPreview.read(auth,session.toString(),s->response(files.professor(id,s.getTarget()),download));
    }
    @ExceptionHandler(AdminApiException.class) public ResponseEntity<?> modeError(AdminApiException error) {
        return ResponseEntity.status(error.code.status).body(java.util.Map.of("code",error.code.name(),"message",error.code.message));
    }
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> error(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(java.util.Map.of("code",java.util.Objects.toString(error.getReason(),"MATERIAL_UNAVAILABLE")));
    }
}
