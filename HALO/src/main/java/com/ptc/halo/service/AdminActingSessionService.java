package com.ptc.halo.service;

import com.ptc.halo.dtoRequest.AdminActingSessionRequest;
import com.ptc.halo.dtoResponse.*;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
import static com.ptc.halo.service.AdminApiException.Code.*;

@Service
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Transactional(readOnly = true)
public class AdminActingSessionService {
    public static final Duration LIFETIME = Duration.ofMinutes(30);
    private final UserRepository users;
    private final AdminActingSessionRepository sessions;
    private final ActivityLogService audit;
    private final java.util.Map<Long,AdminActingSessionEntity> superPreviews = new java.util.concurrent.ConcurrentHashMap<>();
    public AdminActingSessionService(UserRepository users, AdminActingSessionRepository sessions, ActivityLogService audit) {
        this.users = users; this.sessions = sessions; this.audit = audit;
    }
    private UserEntity admin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        if (authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN")))
            throw new AccessDeniedException("Admin access required");
        var actor = com.ptc.halo.security.AuthenticatedAccount.find(users, authentication)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Authentication required"));
        if ((actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN) || actor.getStatus() != Status.ACTIVE || actor.isMustChangePassword())
            throw new AccessDeniedException("Admin access required");
        return actor;
    }
    private void role(UserEntity actor, Role role) {
        if (role != Role.PROFESSOR && role != Role.STUDENT && !(actor.getRole()==Role.SUPER_ADMIN && role==Role.ADMIN)) throw new AdminApiException(INVALID_TARGET_ROLE);
    }
    public Page<AdminActingTargetResponse> targets(Authentication authentication, Role role, int page, int size, String search) {
        var actor=admin(authentication); role(actor,role);
        if (page < 0 || size < 1 || search == null || search.length() > 100) throw new AdminApiException(ACTING_INPUT_INVALID);
        return users.findActingTargets(role, search.trim().toLowerCase(java.util.Locale.ROOT),
                PageRequest.of(page, Math.min(size, 100), Sort.by("id")));
    }
    @Transactional
    public AdminActingSessionResponse create(Authentication authentication, AdminActingSessionRequest request) {
        var actor = admin(authentication);
        if (request == null || request.targetUserId() == null || request.targetUserId() <= 0)
            throw new AdminApiException(ACTING_INPUT_INVALID);
        role(actor,request.targetRole());
        requireNoSuperMode(actor);
        var target = users.findById(request.targetUserId()).orElseThrow(() -> new AdminApiException(RESOURCE_NOT_FOUND));
        if (target.getRole() != request.targetRole()) throw new AdminApiException(INVALID_TARGET_ROLE);
        if (target.getStatus() != Status.ACTIVE || (actor.getRole()==Role.SUPER_ADMIN && target.isMustChangePassword())) throw new AdminApiException(ACTING_TARGET_INACTIVE);
        if(target.getRole()==Role.STUDENT && users.countCompleteStudentProfile(target.getId())==0) throw new AdminApiException(PROFILE_INCOMPLETE);
        var now = Instant.now();
        var session = sessions.save(new AdminActingSessionEntity(UUID.randomUUID().toString(), actor, target, now, now.plus(LIFETIME)));
        audit.createActingLog(session, "Started acting session");
        return response(session);
    }
    public AdminActingSessionResponse validate(Authentication authentication, String id) {
        var actor = admin(authentication);
        var session = sessions.findByIdAndAdmin_Id(id, actor.getId()).orElseThrow(() -> new AdminApiException(RESOURCE_NOT_FOUND));
        checkSession(actor, session);
        return response(session);
    }
    private void checkSession(UserEntity actor, AdminActingSessionEntity session) {
        role(actor,session.getTargetRole());
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(Instant.now())
                || session.getAdminTokenVersion() != actor.getTokenVersion()) throw new AdminApiException(ACTING_SESSION_INVALID);
        var target = session.getTarget();
        if (target.getRole() != session.getTargetRole() || target.getStatus() != Status.ACTIVE
                || target.getTokenVersion() != session.getTargetTokenVersion()) throw new AdminApiException(ACTING_SESSION_INVALID);
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public AdminActingSessionEntity requireProfessor(Authentication authentication, String id) {
        var actor = admin(authentication);
        var session = sessions.findForRevocation(id, actor.getId()).orElseThrow(() -> new AdminApiException(RESOURCE_NOT_FOUND));
        checkSession(actor, session);
        if (session.getTargetRole() != Role.PROFESSOR) throw new AdminApiException(INVALID_TARGET_ROLE);
        return session;
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public AdminActingSessionEntity requireStudent(Authentication authentication, String id) {
        var actor = admin(authentication);
        var session = sessions.findForRevocation(id, actor.getId()).orElseThrow(() -> new AdminApiException(RESOURCE_NOT_FOUND));
        checkSession(actor, session);
        if (session.getTargetRole() != Role.STUDENT) throw new AdminApiException(INVALID_TARGET_ROLE);
        return session;
    }
    @Transactional
    public void revoke(Authentication authentication, String id) {
        var actor = admin(authentication);
        var session = sessions.findForRevocation(id, actor.getId()).orElseThrow(() -> new AdminApiException(RESOURCE_NOT_FOUND));
        if (session.getRevokedAt() == null) {
            session.revoke(Instant.now());
            audit.createActingLog(session, "Revoked acting session");
        }
    }
    private AdminActingSessionResponse response(AdminActingSessionEntity session) {
        var target = session.getTarget();
        return new AdminActingSessionResponse(session.getId(), session.getAdmin().getId(),
                new AdminActingTargetResponse(target.getId(), target.getName(), target.getEmail(), session.getTargetRole()),
                session.getCreatedAt(), session.getExpiresAt());
    }

    // Preview credentials are never saved in admin_acting_session. Consequently they
    // cannot authorize the real acting endpoints, even if the client changes its URL.
    @Transactional
    public AdminActingSessionEntity createStudentPreview(Authentication authentication, Long targetId) {
        return createPreview(authentication, targetId, Role.STUDENT);
    }
    @Transactional
    public AdminActingSessionEntity createProfessorPreview(Authentication authentication, Long targetId) {
        return createPreview(authentication, targetId, Role.PROFESSOR);
    }
    private AdminActingSessionEntity createPreview(Authentication authentication, Long targetId, Role role) {
        var actor = admin(authentication);
        requireNoSuperMode(actor);
        if (targetId == null || targetId <= 0) throw new AdminApiException(ACTING_INPUT_INVALID);
        var target = users.findById(targetId).orElseThrow(() -> new AdminApiException(RESOURCE_NOT_FOUND));
        if (target.getRole() != role) throw new AdminApiException(INVALID_TARGET_ROLE);
        if (target.getStatus() != Status.ACTIVE) throw new AdminApiException(ACTING_TARGET_INACTIVE);
        if (role == Role.STUDENT && users.countCompleteStudentProfile(target.getId()) == 0) throw new AdminApiException(PROFILE_INCOMPLETE);
        var now = Instant.now();
        var context = new AdminActingSessionEntity(UUID.randomUUID().toString(), actor, target, now, now.plus(LIFETIME));
        audit.createPreviewLog(actor, target, "Started " + (role == Role.STUDENT ? "Student" : "Professor") + " preview");
        if(actor.getRole()==Role.SUPER_ADMIN) {
            superPreviews.put(actor.getId(),context);
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
                @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)superPreviews.remove(actor.getId(),context);}
            });
        }
        return context;
    }

    public UserEntity validateStudentPreview(Authentication authentication, AdminActingSessionEntity context) {
        return validatePreview(authentication, context, Role.STUDENT);
    }
    public UserEntity validateProfessorPreview(Authentication authentication, AdminActingSessionEntity context) {
        return validatePreview(authentication, context, Role.PROFESSOR);
    }
    private UserEntity validatePreview(Authentication authentication, AdminActingSessionEntity context, Role role) {
        var actor = admin(authentication);
        if (!actor.getId().equals(context.getAdmin().getId())) throw new AdminApiException(RESOURCE_NOT_FOUND);
        var target = users.findById(context.getTarget().getId()).orElseThrow(() -> new AdminApiException(ACTING_SESSION_INVALID));
        if (context.getRevokedAt() != null || !context.getExpiresAt().isAfter(Instant.now())
                || context.getTargetRole() != role || target.getRole() != role
                || target.getStatus() != Status.ACTIVE || actor.getTokenVersion() != context.getAdminTokenVersion()
                || target.getTokenVersion() != context.getTargetTokenVersion()) throw new AdminApiException(ACTING_SESSION_INVALID);
        return target;
    }

    @Transactional
    public void endProfessorPreview(Authentication authentication, AdminActingSessionEntity context) {
        var target = validateProfessorPreview(authentication, context);
        audit.createPreviewLog(admin(authentication), target, "Ended Professor preview");
        superPreviews.remove(context.getAdmin().getId(),context);
    }

    @Transactional
    public void endStudentPreview(Authentication authentication, AdminActingSessionEntity context) {
        var target = validateStudentPreview(authentication, context);
        audit.createPreviewLog(admin(authentication), target, "Ended Student preview");
        superPreviews.remove(context.getAdmin().getId(),context);
    }

    // The actor row serializes Super Admin mode creation across real and ephemeral modes.
    // Existing Admin session behavior is unchanged.
    private void requireNoSuperMode(UserEntity actor) {
        if(actor.getRole()!=Role.SUPER_ADMIN)return;
        users.findForAssessmentLifecycle(actor.getId()).orElseThrow(()->new AdminApiException(ACTING_SESSION_INVALID));
        var preview=superPreviews.get(actor.getId());
        var previewTarget=preview==null ? null : users.findById(preview.getTarget().getId()).orElse(null);
        if(preview!=null && (preview.getAdminTokenVersion()!=actor.getTokenVersion() || preview.getRevokedAt()!=null || !preview.getExpiresAt().isAfter(Instant.now())
                || previewTarget==null || previewTarget.getStatus()!=Status.ACTIVE || previewTarget.getRole()!=preview.getTargetRole() || previewTarget.getTokenVersion()!=preview.getTargetTokenVersion())) {
            superPreviews.remove(actor.getId(),preview);preview=null;
        }
        if(preview!=null || !sessions.findActiveModesForUpdate(actor.getId(),actor.getTokenVersion(),Instant.now()).isEmpty())
            throw new AdminApiException(ACTING_MODE_ALREADY_ACTIVE);
    }
    public record ActiveMode(AdminActingSessionResponse session, boolean preview) {}
    @Transactional
    public java.util.List<ActiveMode> activeModes(Authentication authentication) {
        var actor=admin(authentication);
        if(actor.getRole()!=Role.SUPER_ADMIN)throw new AccessDeniedException("Super Admin required");
        users.findForAssessmentLifecycle(actor.getId()).orElseThrow(()->new AdminApiException(ACTING_SESSION_INVALID));
        var result=new java.util.ArrayList<ActiveMode>();
        var preview=superPreviews.get(actor.getId());
        if(preview!=null) {
            try {validatePreview(authentication,preview,preview.getTargetRole());result.add(new ActiveMode(response(preview),true));}
            catch(AdminApiException invalid){superPreviews.remove(actor.getId(),preview);}
        }
        sessions.findActiveModesForUpdate(actor.getId(),actor.getTokenVersion(),Instant.now()).forEach(s->result.add(new ActiveMode(response(s),false)));
        return result;
    }
    // Recovery also works when a create response was lost before the client received its ID.
    @Transactional
    public void exitActiveMode(Authentication authentication,String id) {
        var actor=admin(authentication);
        if(actor.getRole()!=Role.SUPER_ADMIN)throw new AccessDeniedException("Super Admin required");
        users.findForAssessmentLifecycle(actor.getId()).orElseThrow(()->new AdminApiException(ACTING_SESSION_INVALID));
        var preview=superPreviews.get(actor.getId());
        if(preview!=null && preview.getId().equals(id)) {
            audit.createPreviewLog(actor,preview.getTarget(),"Ended " + (preview.getTargetRole()==Role.STUDENT?"Student":"Professor") + " preview");
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
                @Override public void afterCommit(){preview.revoke(Instant.now());superPreviews.remove(actor.getId(),preview);}
            });
        } else revoke(authentication,id);
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public AdminActingSessionEntity requireAdmin(Authentication authentication,String id) {
        var actor=admin(authentication);
        if(actor.getRole()!=Role.SUPER_ADMIN)throw new AccessDeniedException("Super Admin required");
        var session=sessions.findForRevocation(id,actor.getId()).orElseThrow(()->new AdminApiException(RESOURCE_NOT_FOUND));
        checkSession(actor,session);
        if(session.getTargetRole()!=Role.ADMIN)throw new AdminApiException(INVALID_TARGET_ROLE);
        return session;
    }
}
