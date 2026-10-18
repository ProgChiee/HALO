package com.ptc.halo.controller;
import com.ptc.halo.service.*;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
@RestController
@RequestMapping({"/api/professor/ai-learning-modules/{id}/quiz-revision","/api/admin/acting/professor/ai-learning-modules/{id}/quiz-revision"})
@PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
public class QuizRevisionController extends ProfessorErrorResponses {
 private final QuizRevisionService quizzes;private final UserRepository users;private final AdminProfessorModeService acting;private final TransactionTemplate tx;
 public QuizRevisionController(QuizRevisionService quizzes,UserRepository users,AdminProfessorModeService acting,PlatformTransactionManager manager){this.quizzes=quizzes;this.users=users;this.acting=acting;tx=new TransactionTemplate(manager);}
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

 public record Approval(@NotNull Long draftId){}
 @GetMapping public Object get(@PathVariable Long id,Authentication auth,HttpServletRequest request){return access(auth,request,(p,a)->quizzes.get(id,p));}
 @PostMapping("/generate") public Object generate(@PathVariable Long id,Authentication auth,HttpServletRequest request){var captured=access(auth,request,(p,a)->quizzes.prepare(id,p));var generated=quizzes.generate(captured);return access(auth,request,(p,a)->quizzes.save(id,captured,generated,p,a));}
 @PostMapping("/approve") public Object approve(@PathVariable Long id,@Valid @RequestBody Approval body,Authentication auth,HttpServletRequest request){return access(auth,request,(p,a)->quizzes.approve(id,body.draftId(),p,a));}
 @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
 public org.springframework.http.ResponseEntity<?> invalid(){return ProfessorErrorResponses.reply(400,"INVALID_REQUEST");}
}
