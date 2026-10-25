package com.ptc.halo.controller;

import com.ptc.halo.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
public class ProfilePhotoController extends StudentLessonExceptionHandler {
    private final ProfilePhotoService photos;
    private final com.ptc.halo.repository.UserRepository users;
    private final AdminProfessorModeService professor;
    private final AdminStudentModeService support;
    private final AdminStudentPreviewService preview;
    public ProfilePhotoController(ProfilePhotoService photos, com.ptc.halo.repository.UserRepository users, AdminStudentModeService support, AdminStudentPreviewService preview, AdminProfessorModeService professor) { this.photos=photos; this.users=users; this.support=support; this.preview=preview; this.professor=professor; }
    @GetMapping("/api/admin/acting/professor/profile/photo") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> professor(Authentication auth, @RequestHeader("X-Acting-Session") java.util.UUID session) {
        return professor.execute(auth, session.toString(), s -> read(s.getTarget().getId()));
    }
    private long owner(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
        var user = com.ptc.halo.security.AuthenticatedAccount.find(users, auth).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED"));
        if (user.getStatus() != com.ptc.halo.enums.Status.ACTIVE || user.getRole() == null
                || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_" + user.getRole().name())))
            throw new org.springframework.security.access.AccessDeniedException("Account unavailable");
        return user.getId();
    }
    @GetMapping("/api/admin/acting/student/profile/photo") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> support(Authentication auth, @RequestHeader("X-Acting-Session") java.util.UUID session) {
        return support.execute(auth, session.toString(), s -> read(s.getTarget().getId()));
    }
    @GetMapping("/api/admin/preview/student/profile/photo") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> preview(Authentication auth, @RequestHeader("X-Acting-Session") java.util.UUID session) {
        return preview.read(auth, session.toString(), s -> read(s.getId()));
    }
    @ExceptionHandler(AdminApiException.class)
    public ResponseEntity<?> actingFailure(AdminApiException error) { return ResponseEntity.status(error.code.status).body(java.util.Map.of("status", error.code.status, "code", error.code.name(), "message", error.code.message)); }
    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    public ResponseEntity<?> missingSession() { return ResponseEntity.badRequest().body(java.util.Map.of("status",400,"code","INVALID_REQUEST","message","An acting session is required.")); }
    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public ResponseEntity<?> invalidUpload() { return ResponseEntity.badRequest().body(java.util.Map.of("status",400,"code","INVALID_PROFILE_PHOTO","message","Please choose a readable JPG, PNG or WEBP photo up to 5 MB.")); }
    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<?> missingUpload() { return invalidUpload(); }
    private ResponseEntity<byte[]> read(long id) {
        byte[] image;
        try { image = photos.read(id); } catch (IOException error) { throw new org.springframework.web.server.ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PHOTO_UNAVAILABLE"); }
        return ResponseEntity.status(image == null ? 204 : 200).contentType(MediaType.IMAGE_PNG)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(image);
    }
    @GetMapping({"/api/profile/photo", "/api/student/profile/photo", "/api/professor/profile/photo"}) @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PROFESSOR','STUDENT')")
    public ResponseEntity<byte[]> photo(Authentication auth) throws IOException {
        return read(owner(auth));
    }
    @PutMapping({"/api/profile/photo", "/api/student/profile/photo"}) @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PROFESSOR','STUDENT')")
    public ResponseEntity<Void> save(Authentication auth, @RequestParam("photo") MultipartFile photo, @RequestHeader(value="X-Acting-Session", required=false) String actingSession) throws IOException {
        if (actingSession != null) throw new org.springframework.security.access.AccessDeniedException("Acting photos are read-only");
        photos.save(owner(auth), photo);
        return ResponseEntity.noContent().build();
    }
}
