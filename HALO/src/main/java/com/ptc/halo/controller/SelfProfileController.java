package com.ptc.halo.controller;

import com.ptc.halo.service.SelfProfileService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
public class SelfProfileController extends StudentLessonExceptionHandler {
 private final SelfProfileService profiles;
 public SelfProfileController(SelfProfileService profiles) { this.profiles=profiles; }
 @PatchMapping("/api/profile")
 @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PROFESSOR','STUDENT')")
 public SelfProfileService.NameResponse update(Authentication auth,@RequestBody Map<String,Object> body,
   @RequestHeader(value="X-Acting-Session",required=false) String actingSession) {
  if(actingSession!=null) throw new org.springframework.security.access.AccessDeniedException("Acting profiles are read-only");
  if(body==null || !body.keySet().equals(java.util.Set.of("fullName")) || !(body.get("fullName") instanceof String))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_PROFILE_UPDATE");
  return profiles.updateName(auth,(String)body.get("fullName"));
 }

 @Override
 @ExceptionHandler(ResponseStatusException.class)
 public ResponseEntity<Map<String,Object>> status(ResponseStatusException error,jakarta.servlet.http.HttpServletRequest request) {
  String code=error.getReason()==null?"REQUEST_FAILED":error.getReason();
  String message=switch(code) {
   case "FULL_NAME_REQUIRED" -> "Full name is required.";
   case "FULL_NAME_TOO_LONG" -> "Full name must be 100 characters or fewer.";
   case "INVALID_FULL_NAME" -> "Use letters, spaces, hyphens, apostrophes, periods, and commas. Include at least one letter.";
   case "INVALID_PROFILE_UPDATE" -> "Submit only a full name as text.";
   default -> null;
  };
  if(message==null) return super.status(error,request);
  return ResponseEntity.status(error.getStatusCode()).body(Map.of("status",error.getStatusCode().value(),"code",code,"message",message));
 }
}
