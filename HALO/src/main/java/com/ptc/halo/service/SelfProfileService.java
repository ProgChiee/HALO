package com.ptc.halo.service;

import com.ptc.halo.enums.*;
import com.ptc.halo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class SelfProfileService {
 private final UserRepository users;
 private final ActivityLogService activity;
 public SelfProfileService(UserRepository users, ActivityLogService activity) { this.users=users;this.activity=activity; }
 public record NameResponse(Long userId, String name) {}
 @Transactional
 public NameResponse updateName(Authentication auth, String fullName) {
  if(auth==null || !auth.isAuthenticated()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED");
  var user=com.ptc.halo.security.AuthenticatedAccount.find(users, auth).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED"));
  if(user.getStatus()!=Status.ACTIVE || user.getRole()==null || auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_"+user.getRole().name())))
   throw new org.springframework.security.access.AccessDeniedException("Account unavailable");
  String name=FullNameValidator.normalize(fullName);
  if(!name.equals(user.getName())) {
   if(users.updateOwnName(user.getId(),name)!=1) throw new org.springframework.security.access.AccessDeniedException("Account unavailable");
   user.setName(name);
   activity.createLog(user,ActivityType.ACCOUNT,"Updated their profile name");
  }
  return new NameResponse(user.getId(),user.getName());
 }
}
