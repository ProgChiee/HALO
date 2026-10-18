package com.ptc.halo.service;
import com.ptc.halo.entity.*;
import com.ptc.halo.dtoResponse.*;
import com.ptc.halo.enums.Role;
import com.ptc.halo.repository.ProfessorRepository;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Process-local, bounded preview credentials never enter the real acting-session repository. */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminProfessorPreviewService {
 private final AdminActingSessionService authorization;
 private final ProfessorRepository professors;
 private final TransactionTemplate reads, lifecycle;
 private final Map<String,AdminActingSessionEntity> previews=new ConcurrentHashMap<>();
 public AdminProfessorPreviewService(AdminActingSessionService authorization,ProfessorRepository professors,PlatformTransactionManager manager){
  this.authorization=authorization;this.professors=professors;
  reads=new TransactionTemplate(manager);reads.setReadOnly(true);lifecycle=new TransactionTemplate(manager);
 }
 private void prune(){previews.entrySet().removeIf(e->!e.getValue().getExpiresAt().isAfter(Instant.now()));}
 private AdminActingSessionEntity context(String id){prune();var s=previews.get(id);if(s==null)throw new AdminApiException(AdminApiException.Code.ACTING_SESSION_INVALID);return s;}
 public synchronized AdminActingSessionResponse create(Authentication auth,Long target){
  prune();if(previews.size()>=100)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"PREVIEW_LIMIT_REACHED");
  var s=lifecycle.execute(tx->{var created=authorization.createProfessorPreview(auth,target);professors.findByUserId(created.getTarget().getId()).orElseThrow(()->new AdminApiException(AdminApiException.Code.RESOURCE_NOT_FOUND));return created;});
  previews.put(s.getId(),s);return validate(auth,s.getId());
 }
 public synchronized <T> T read(Authentication auth,String id,Function<AdminActingSessionEntity,T> work){
  var s=context(id);return reads.execute(tx->{authorization.validateProfessorPreview(auth,s);professors.findByUserId(s.getTarget().getId()).orElseThrow(()->new AdminApiException(AdminApiException.Code.RESOURCE_NOT_FOUND));return work.apply(s);});
 }
 public AdminActingSessionResponse validate(Authentication auth,String id){return read(auth,id,s->new AdminActingSessionResponse(s.getId(),s.getAdmin().getId(),new AdminActingTargetResponse(s.getTarget().getId(),s.getTarget().getName(),s.getTarget().getEmail(),Role.PROFESSOR),s.getCreatedAt(),s.getExpiresAt()));}
 public synchronized void revoke(Authentication auth,String id){var s=context(id);lifecycle.executeWithoutResult(tx->authorization.endProfessorPreview(auth,s));s.revoke(Instant.now());previews.remove(id);}
}
