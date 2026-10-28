package com.ptc.halo.service;

import com.ptc.halo.dtoRequest.StudentAcademicUpdateRequest;
import com.ptc.halo.dtoResponse.StudentProfileResponse;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.security.AuthenticatedAccount;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentAcademicProfileService {
    private final UserRepository users;
    private final StudentProfileRepository profiles;
    private final ProfileService profileService;
    private final ActivityLogService activity;

    public StudentAcademicProfileService(UserRepository users, StudentProfileRepository profiles,
            ProfileService profileService, ActivityLogService activity) {
        this.users = users; this.profiles = profiles; this.profileService = profileService; this.activity = activity;
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public StudentProfileResponse update(Authentication auth, StudentAcademicUpdateRequest request) {
        // Same account lock as lifecycle changes and Admin profile recovery.
        // Resolve fresh state only after acquiring it, so concurrent field edits cannot lose each other.
        var user = AuthenticatedAccount.lock(users, auth);
        if (user.getRole() != Role.STUDENT) throw new org.springframework.security.access.AccessDeniedException("Student access required");
        var profile = profiles.findByUserId(user.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "PROFILE_INCOMPLETE"));
        if (request == null || request.field() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        switch (request.field()) {
            case STUDENT_ID -> {
                String id = StudentRegistrationValidator.validateStudentId(request.value());
                if (!id.equals(profile.getStudentId()) && profiles.existsByStudentId(id))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "STUDENT_ID_CONFLICT");
                profile.setStudentId(id);
            }
            case SECTION -> profile.setSection(StudentRegistrationValidator.validateSection(request.value()));
            case YEAR_LEVEL -> {
                try { profile.setYearLevel(YearLevel.valueOf(request.value() == null ? "" : request.value())); }
                catch (IllegalArgumentException error) {
                    throw new RegistrationValidationException(java.util.Map.of("yearLevel", "Select your year level."), 400);
                }
            }
        }
        // Flush while inside this transaction. The existing unique DB constraint closes the
        // race between different students choosing the same ID; rejected writes roll back.
        profiles.saveAndFlush(profile);
        activity.createLog(user, ActivityType.ACCOUNT, "Updated their academic profile");
        return profileService.getStudentProfile(user);
    }
}
