package com.ptc.halo.service;
import com.ptc.halo.entity.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import java.util.function.Function;
@Service
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminAdminModeService {
 private final AdminActingSessionService sessions;private final ActivityLogService audit;
 public SuperAdminAdminModeService(AdminActingSessionService sessions,ActivityLogService audit){this.sessions=sessions;this.audit=audit;}
 @Transactional
 public <T>T execute(Authentication auth,String id,Function<UserEntity,T> work){
  var session=sessions.requireAdmin(auth,id);
  return audit.withAdminContext(session,()->work.apply(session.getTarget()));
 }
}
