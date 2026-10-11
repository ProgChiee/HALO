package com.ptc.halo.service;

import com.ptc.halo.entity.AdminActingSessionEntity;
import com.ptc.halo.dtoResponse.*;
import com.ptc.halo.enums.ActivityType;
import com.ptc.halo.repository.StudentProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminStudentModeService {
    private final AdminActingSessionService sessions;
    private final StudentProfileRepository profiles;
    private final MentorService mentor;
    private final ActivityLogService audit;
    private final TransactionTemplate transactions;
    public AdminStudentModeService(AdminActingSessionService sessions, StudentProfileRepository profiles,
            MentorService mentor, ActivityLogService audit, PlatformTransactionManager manager) {
        this.sessions=sessions; this.profiles=profiles; this.mentor=mentor; this.audit=audit;
        this.transactions=new TransactionTemplate(manager);
    }
    @FunctionalInterface public interface Work<T> { T apply(AdminActingSessionEntity session); }
    // HTTP callers can supply only a session ID, never a target Student ID.
    public <T> T execute(Authentication authentication, String id, Work<T> work) {
        return transactions.execute(tx -> {
            var session=sessions.requireStudent(authentication,id);
            profiles.findByUserId(session.getTarget().getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND"));
            return work.apply(session);
        });
    }
    private Runnable guard(Authentication authentication, String id, Long targetId) {
        return () -> execute(authentication,id,s -> {
            if (!s.getTarget().getId().equals(targetId))
                throw new org.springframework.security.access.AccessDeniedException("Invalid acting target");
            return null;
        });
    }
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public MentorConversationResponse openMentor(Authentication authentication, String id, Long moduleId) {
        var student=execute(authentication,id,s -> s.getTarget());
        return mentor.openSession(moduleId,student,guard(authentication,id,student.getId()),
            () -> execute(authentication,id,s -> {
                audit.createActingLog(s,ActivityType.MODULE,"Opened Mentor session for module ID "+moduleId); return null;
            }));
    }
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public MentorSessionResponse sendMessage(Authentication authentication, String id, Long mentorSessionId, String message, String requestId) {
        var student=execute(authentication,id,s -> s.getTarget());
        return mentor.sendMessage(mentorSessionId,student,message,requestId,guard(authentication,id,student.getId()),
            () -> execute(authentication,id,s -> {
                audit.createActingLog(s,ActivityType.MODULE,"Saved Mentor exchange in session ID "+mentorSessionId); return null;
            }));
    }
}
