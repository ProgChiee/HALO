package com.ptc.halo.service;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.AuthEvent;
import com.ptc.halo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Never accepts a password, OTP or reset payload. Audit failures cannot fail authentication. */
@Service
public class AuthAuditService {
 private final UserRepository users;private final ActivityLogService logs;private final TransactionTemplate tx;
 private static final org.slf4j.Logger logger=org.slf4j.LoggerFactory.getLogger(AuthAuditService.class);
 public AuthAuditService(UserRepository users,ActivityLogService logs,PlatformTransactionManager manager){this.users=users;this.logs=logs;tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.setTimeout(3);}
 public void record(AuthEvent event,String email){record(event,email,null);}
 private void record(AuthEvent event,String email,String key){
  String identifier=safeIdentifier(email);
  Runnable write=()->{try{tx.executeWithoutResult(status->{var user=identifier==null?null:users.findByEmail(identifier).orElse(null);logs.createAuthLog(user,event,identifier,key);});}
   catch(RuntimeException failure){logger.warn("Authentication audit unavailable; event={} exceptionType={}",event,failure.getClass().getSimpleName());}};
  if(TransactionSynchronizationManager.isActualTransactionActive()&&TransactionSynchronizationManager.isSynchronizationActive())
   TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){write.run();}});
  else write.run();
 }
 public void logout(String email,String bearer){
  // Only a one-way deduplication fingerprint is stored; never the bearer token itself.
  try{String key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bearer.getBytes(StandardCharsets.UTF_8)));record(AuthEvent.LOGOUT,email,key);}
  catch(java.security.NoSuchAlgorithmException error){logger.warn("Authentication audit unavailable; event=LOGOUT");}
 }
 static String safeIdentifier(String email){return email!=null&&email.length()<=254&&email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")?email:null;}
 public static String description(AuthEvent event,UserEntity user,String identifier){
  String name=user==null?"Unknown account":user.getName()==null||user.getName().isBlank()?"User":user.getName().replaceAll("[\\r\\n\\t]"," ");
  if(name.length()>150)name=name.substring(0,150);
  return switch(event){
   case LOGIN_SUCCESS->name+" logged in";
   case LOGIN_FAILED->"Failed login attempt for "+(identifier==null?"an unknown account":identifier);
   case LOGOUT->name+" logged out";
   case PASSWORD_RESET_REQUESTED->name+" requested a password reset";
   case PASSWORD_RESET_COMPLETED->name+" completed a password reset";
   case OTP_VERIFIED->name+" verified a password-reset OTP";
   case ACCOUNT_INACTIVE_LOGIN_ATTEMPT->"Login blocked because "+name+"'s account is inactive";
   case ACCOUNT_ARCHIVED_LOGIN_ATTEMPT->"Login blocked because "+name+"'s account is archived";
   case ACCOUNT_BLOCKED_LOGIN_ATTEMPT->"Login blocked because "+name+"'s account is blocked";
   case ACCOUNT_LOGIN_BLOCKED->"Login blocked for "+name;
  };
 }
}
