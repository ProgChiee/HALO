package com.ptc.halo.controller;
import com.ptc.halo.service.*;
import com.ptc.halo.repository.UserRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

@RestController
public class ProfessorEngagementController extends ProfessorErrorResponses {
 private final ProfessorEngagementService engagement;
 private final UserRepository users;
 private final AdminProfessorModeService acting;
 public ProfessorEngagementController(ProfessorEngagementService engagement,UserRepository users,AdminProfessorModeService acting){this.engagement=engagement;this.users=users;this.acting=acting;}
 @GetMapping("/api/professor/students/engagement")
 @PreAuthorize("hasRole('PROFESSOR')")
 public Object get(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
  if(auth==null)throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Authentication required");
  return engagement.get(users.findByEmail(auth.getName()).orElseThrow(()->new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Authentication required")),page,size);
 }
 @GetMapping("/api/admin/acting/professor/students/engagement")
 @PreAuthorize("hasRole('ADMIN')")
 public Object acting(Authentication auth,@RequestHeader("X-Acting-Session") java.util.UUID session,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
  return acting.execute(auth,session.toString(),s->engagement.get(s.getTarget(),page,size));
 }
}
