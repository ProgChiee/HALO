package com.ptc.halo.service;

import com.ptc.halo.dtoRequest.ProfessorIdUpdateRequest;
import com.ptc.halo.dtoResponse.ProfessorProfileResponse;
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
public class ProfessorIdProfileService {
    private final UserRepository users;
    private final ProfessorRepository professors;
    private final ProfileService profiles;
    private final ActivityLogService activity;
    public ProfessorIdProfileService(UserRepository users, ProfessorRepository professors,
            ProfileService profiles, ActivityLogService activity) {
        this.users=users;this.professors=professors;this.profiles=profiles;this.activity=activity;
    }
    @Transactional
    @PreAuthorize("hasRole('PROFESSOR')")
    public ProfessorProfileResponse update(Authentication auth, ProfessorIdUpdateRequest request) {
        // Shares the account lock used by Admin edits, lifecycle and credential updates.
        var user=AuthenticatedAccount.lock(users,auth);
        if(user.getRole()!=Role.PROFESSOR) throw new org.springframework.security.access.AccessDeniedException("Professor access required");
        String id=request==null?null:request.professorId();
        if(id==null || id.isBlank() || id.length()>255)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_PROFESSOR_ID");
        var professor=professors.findByUserId(user.getId()).orElseThrow(()->
            new ResponseStatusException(HttpStatus.CONFLICT,"PROFILE_INCOMPLETE"));
        if(!id.equals(professor.getProfessorId())) {
            if(professors.existsNormalizedProfessorId(id,professor.getId()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"PROFESSOR_ID_CONFLICT");
            professor.setProfessorId(id);
            // AD08's existing binary normalized unique key closes concurrent duplicate races.
            professors.saveAndFlush(professor);
            activity.createLog(user,ActivityType.ACCOUNT,"Updated their Professor ID");
        }
        return profiles.getProfessorProfile(user);
    }
}
