package com.ptc.halo.controller;

import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.security.AuthenticatedAccount;
import com.ptc.halo.service.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
public class SubjectCoverController {
    private final SubjectCoverService covers;
    private final UserRepository users;
    private final StudentLessonAccessService studentAccess;
    private final AdminProfessorModeService professorMode;
    private final AdminProfessorPreviewService professorPreview;
    private final AdminStudentModeService studentMode;
    private final AdminStudentPreviewService studentPreview;
    public SubjectCoverController(SubjectCoverService covers,UserRepository users,StudentLessonAccessService studentAccess,AdminProfessorModeService professorMode,AdminProfessorPreviewService professorPreview,AdminStudentModeService studentMode,AdminStudentPreviewService studentPreview){this.covers=covers;this.users=users;this.studentAccess=studentAccess;this.professorMode=professorMode;this.professorPreview=professorPreview;this.studentMode=studentMode;this.studentPreview=studentPreview;}
    private UserEntity actor(Authentication auth){return AuthenticatedAccount.find(users,auth).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));}
    private ResponseEntity<byte[]> image(byte[] bytes){return ResponseEntity.status(bytes==null?204:200).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").contentType(MediaType.IMAGE_PNG).body(bytes);}
    @GetMapping("/api/professor/subjects/{id}/cover") @PreAuthorize("hasRole('PROFESSOR')")
    public ResponseEntity<byte[]> professor(Authentication auth,@PathVariable long id){return image(covers.professorCover(actor(auth),id));}
    @PutMapping("/api/professor/subjects/{id}/cover") @PreAuthorize("hasRole('PROFESSOR')")
    public ResponseEntity<Void> upload(Authentication auth,@PathVariable long id,@RequestParam("image") MultipartFile file){covers.change(actor(auth),id,file,null);return ResponseEntity.noContent().build();}
    @DeleteMapping("/api/professor/subjects/{id}/cover") @PreAuthorize("hasRole('PROFESSOR')")
    public ResponseEntity<Void> remove(Authentication auth,@PathVariable long id){covers.change(actor(auth),id,null,null);return ResponseEntity.noContent().build();}
    @GetMapping("/api/student/subjects/{id}/cover") @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<byte[]> student(Authentication auth,@PathVariable long id){return image(covers.studentCover(studentAccess.requireStudent(auth),id));}
    @GetMapping("/api/admin/acting/professor/subjects/{id}/cover") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> actingProfessor(Authentication auth,@PathVariable long id,@RequestHeader("X-Acting-Session") UUID session){return professorMode.execute(auth,session.toString(),s->image(covers.professorCover(s.getTarget(),id)));}
    @PutMapping("/api/admin/acting/professor/subjects/{id}/cover") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> actingUpload(Authentication auth,@PathVariable long id,@RequestHeader("X-Acting-Session") UUID session,@RequestParam("image") MultipartFile file){return professorMode.execute(auth,session.toString(),s->{covers.change(s.getTarget(),id,file,s);return ResponseEntity.noContent().build();});}
    @DeleteMapping("/api/admin/acting/professor/subjects/{id}/cover") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> actingRemove(Authentication auth,@PathVariable long id,@RequestHeader("X-Acting-Session") UUID session){return professorMode.execute(auth,session.toString(),s->{covers.change(s.getTarget(),id,null,s);return ResponseEntity.noContent().build();});}
    @GetMapping("/api/admin/preview/professor/subjects/{id}/cover") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> previewProfessor(Authentication auth,@PathVariable long id,@RequestHeader("X-Acting-Session") UUID session){return professorPreview.read(auth,session.toString(),s->image(covers.professorCover(s.getTarget(),id)));}
    @GetMapping("/api/admin/acting/student/subjects/{id}/cover") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> actingStudent(Authentication auth,@PathVariable long id,@RequestHeader("X-Acting-Session") UUID session){return studentMode.execute(auth,session.toString(),s->image(covers.studentCover(s.getTarget(),id)));}
    @GetMapping("/api/admin/preview/student/subjects/{id}/cover") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> previewStudent(Authentication auth,@PathVariable long id,@RequestHeader("X-Acting-Session") UUID session){return studentPreview.read(auth,session.toString(),s->image(covers.studentCover(s,id)));}
    @ExceptionHandler(AdminApiException.class)
    public ResponseEntity<?> modeFailure(AdminApiException error){return ResponseEntity.status(error.code.status).body(java.util.Map.of("code",error.code.name(),"message",error.code.message));}
}
