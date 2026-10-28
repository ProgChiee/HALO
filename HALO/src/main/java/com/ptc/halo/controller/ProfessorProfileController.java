package com.ptc.halo.controller;

import com.ptc.halo.dtoResponse.ProfessorProfileResponse;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/professor")
public class ProfessorProfileController {

    private final ProfileService profileService;
    private final UserRepository userRepository;
    private final com.ptc.halo.service.ProfessorIdProfileService idProfiles;

    public ProfessorProfileController(
            ProfileService profileService,
            UserRepository userRepository,
            com.ptc.halo.service.ProfessorIdProfileService idProfiles) {

        this.profileService = profileService;
        this.userRepository = userRepository;
        this.idProfiles = idProfiles;
    }

    @PatchMapping("/profile/id")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('PROFESSOR')")
    public ResponseEntity<ProfessorProfileResponse> updateId(Authentication authentication,
            @jakarta.validation.Valid @RequestBody com.ptc.halo.dtoRequest.ProfessorIdUpdateRequest request,
            @RequestHeader(value="X-Acting-Session",required=false) String actingSession) {
        if(actingSession!=null) throw new org.springframework.security.access.AccessDeniedException("Acting profiles are read-only");
        return ResponseEntity.ok(idProfiles.update(authentication,request));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<?> duplicateId(org.springframework.dao.DataIntegrityViolationException error) {
        return ResponseEntity.status(409).body(java.util.Map.of("code","PROFESSOR_ID_CONFLICT",
                "message","This Professor ID is already in use. Enter a different Professor ID."));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<?> profileError(org.springframework.web.server.ResponseStatusException error) {
        if (java.util.Set.of("PROFESSOR_ID_CONFLICT", "PROFILE_INCOMPLETE", "INVALID_PROFESSOR_ID").contains(error.getReason() == null ? "" : error.getReason()))
            return ResponseEntity.status(error.getStatusCode()).body(java.util.Map.of("code", error.getReason(), "message", "Please check your Professor ID and try again."));
        return ProfessorErrorResponses.status(error);
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfessorProfileResponse> getProfile(
            Authentication authentication) {

        UserEntity professor =
                com.ptc.halo.security.AuthenticatedAccount.find(userRepository, authentication)
                        .orElseThrow(() ->
                                new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Authentication required")
                        );

        return ResponseEntity.ok(
                profileService.getProfessorProfile(professor)
        );
    }
}

/*
    @PutMapping("/profile")
    public ResponseEntity<ProfessorProfileResponse> updateProfile(
            @RequestBody ProfessorProfileUpdateRequest request,
            Authentication authentication) {

        UserEntity professor =
                userRepository
                        .findByEmail(authentication.getName())
                        .orElseThrow(() ->
                                new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Authentication required")
                        );

        return ResponseEntity.ok(
                profileService.updateProfessorProfile(
                        professor,
                        request
                )
        );
    }
}

@PutMapping("/profile")
public ResponseEntity<ProfessorProfileResponse> updateProfile(
        @RequestBody ProfessorProfileUpdateRequest request,
        Authentication authentication) {

    UserEntity professor =
            userRepository
                    .findByEmail(authentication.getName())
                    .orElseThrow(() ->
                            new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Authentication required")
                    );

    return ResponseEntity.ok(
            profileService.updateProfessorProfile(
                    professor,
                    request
            )
    );
}
}

 */
