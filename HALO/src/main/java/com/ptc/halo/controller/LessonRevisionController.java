package com.ptc.halo.controller;

import com.ptc.halo.service.*;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.dtoRequest.AiLearningModuleUpdateRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.*;

@RestController
@RequestMapping({"/api/professor/ai-learning-modules/{id}/revision","/api/admin/acting/professor/ai-learning-modules/{id}/revision"})
@PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
public class LessonRevisionController extends ProfessorErrorResponses {
 private final LessonRevisionService revisions;private final UserRepository users;private final AdminProfessorModeService acting;private final TransactionTemplate tx;
 public LessonRevisionController(LessonRevisionService revisions,UserRepository users,AdminProfessorModeService acting,PlatformTransactionManager manager){this.revisions=revisions;this.users=users;this.acting=acting;tx=new TransactionTemplate(manager);}
 @FunctionalInterface interface Work<T>{T run(UserEntity professor,AdminActingSessionEntity session)throws Exception;}
 private <T>T access(Authentication auth,HttpServletRequest request,Work<T> work){
  if(request.getRequestURI().startsWith("/api/admin/acting/")){
   if(auth==null||auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))throw new AccessDeniedException("Admin required");
   return acting.execute(auth,request.getHeader("X-Acting-Session"),s->work.run(s.getTarget(),s));
  }
  return tx.execute(status->{
   if(auth==null||request.getHeader("X-Acting-Session")!=null||auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_PROFESSOR")))throw new AccessDeniedException("Professor required");
   var p=users.findByEmail(auth.getName()).filter(u->u.getRole()==Role.PROFESSOR&&u.getStatus()==Status.ACTIVE).orElseThrow(()->new AccessDeniedException("Professor unavailable"));
   try{return work.run(p,null);}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("Revision operation failed",e);}
  });
 }
 public record Version(@NotNull Long version){}
 public record Save(@NotNull Long version,@NotNull @Valid AiLearningModuleUpdateRequest materials,@NotNull List<String> keepFiles){}
 @GetMapping public Object get(@PathVariable Long id,Authentication auth,HttpServletRequest request){return access(auth,request,(p,a)->revisions.get(id,p));}
 @PostMapping public Object start(@PathVariable Long id,Authentication auth,HttpServletRequest request){return access(auth,request,(p,a)->revisions.start(id,p,a));}
 @PutMapping(consumes="multipart/form-data") public Object save(@PathVariable Long id,@Valid @RequestPart("revision") Save save,@RequestPart(value="files",required=false) List<MultipartFile> files,Authentication auth,HttpServletRequest request){
  return access(auth,request,(p,a)->revisions.save(id,save.version(),save.materials(),save.keepFiles(),files,p,a));
 }
 @DeleteMapping @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 public void discard(@PathVariable Long id,@RequestParam Long version,Authentication auth,HttpServletRequest request){access(auth,request,(p,a)->{revisions.discard(id,version,p,a);return null;});}
 @PostMapping("/review") public Object review(@PathVariable Long id,@Valid @RequestBody Version version,Authentication auth,HttpServletRequest request){
  var captured=access(auth,request,(p,a)->revisions.prepare(id,version.version(),p,false));
  var generated=revisions.generate(captured);var fingerprint=revisions.fingerprint(generated);
  return access(auth,request,(p,a)->revisions.reviewed(id,generated,p,a,fingerprint));
 }
 @PostMapping("/publish") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 public void publish(@PathVariable Long id,@Valid @RequestBody Version version,Authentication auth,HttpServletRequest request){
  var captured=access(auth,request,(p,a)->revisions.prepare(id,version.version(),p,true));
  var fingerprint=revisions.fingerprint(captured);var assessmentChanged=revisions.assessmentChanged(captured);
  access(auth,request,(p,a)->{revisions.publish(id,captured,fingerprint,p,a,assessmentChanged);return null;});
 }

 @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
 public org.springframework.http.ResponseEntity<?> invalidRevision(){return ProfessorErrorResponses.reply(400,"INVALID_REQUEST");}
}
