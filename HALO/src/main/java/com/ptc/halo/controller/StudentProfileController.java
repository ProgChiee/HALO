package com.ptc.halo.controller;

import com.ptc.halo.dtoResponse.StudentProfileResponse;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student")
public class StudentProfileController {

    private final ProfileService profileService;
    private final UserRepository userRepository;
    private final com.ptc.halo.service.StudentAcademicProfileService academicProfileService;

    public StudentProfileController(
            ProfileService profileService,
            UserRepository userRepository,
            com.ptc.halo.service.StudentAcademicProfileService academicProfileService) {

        this.profileService = profileService;
        this.userRepository = userRepository;
        this.academicProfileService = academicProfileService;
    }

    @PatchMapping("/profile/academic")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StudentProfileResponse> updateAcademicProfile(Authentication authentication,
            @RequestBody com.ptc.halo.dtoRequest.StudentAcademicUpdateRequest request,
            @RequestHeader(value = "X-Acting-Session", required = false) String actingSession) {
        if (actingSession != null) throw new org.springframework.security.access.AccessDeniedException("Acting profiles are read-only");
        return ResponseEntity.ok(academicProfileService.update(authentication, request));
    }

    @ExceptionHandler(com.ptc.halo.service.RegistrationValidationException.class)
    public ResponseEntity<?> academicValidation(com.ptc.halo.service.RegistrationValidationException error) {
        return ResponseEntity.status(error.status).body(java.util.Map.of("code", "ACADEMIC_PROFILE_VALIDATION_FAILED",
                "message", "Please correct the indicated field.", "errors", error.errors));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<?> duplicateStudentId(org.springframework.dao.DataIntegrityViolationException error) {
        return ResponseEntity.status(409).body(java.util.Map.of("code", "STUDENT_ID_CONFLICT",
                "message", "This Student ID is already registered."));
    }


    @GetMapping("/profile")
    public ResponseEntity<StudentProfileResponse> getProfile(
            Authentication authentication) {

        UserEntity student =
                com.ptc.halo.security.AuthenticatedAccount.find(userRepository, authentication)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Student not found"
                                )
                        );

        return ResponseEntity.ok(
                profileService.getStudentProfile(student)
        );
    }
}
