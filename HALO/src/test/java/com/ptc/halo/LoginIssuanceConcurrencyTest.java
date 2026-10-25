package com.ptc.halo;

import com.ptc.halo.dtoRequest.LoginRequest;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="},showSql=false)
@Import({AuthService.class, com.ptc.halo.service.LoginTokenIssuer.class,JwtService.class,LoginIssuanceConcurrencyTest.Config.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class LoginIssuanceConcurrencyTest {
 @TestConfiguration static class Config { @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder(4);} }
 @Autowired AuthService auth; @Autowired UserRepository users; @Autowired PasswordEncoder encoder;
 @Autowired PlatformTransactionManager manager; @SpyBean JwtService jwt;
 @MockBean AuthenticationManager authentication; @MockBean AuthAuditService audit;
 ExecutorService executor;
 @BeforeEach void setup(){executor=Executors.newFixedThreadPool(2);}
 @AfterEach void cleanup(){executor.shutdownNow();}
 UserEntity account(Role role){var u=new UserEntity();u.setName("Synthetic");u.setEmail(UUID.randomUUID()+"@example.test");u.setRole(role);u.setStatus(Status.ACTIVE);u.setPassword(encoder.encode("Original-pass123"));return users.saveAndFlush(u);}
 LoginRequest request(UserEntity u){var r=new LoginRequest();r.setEmail(u.getEmail());r.setPassword("Original-pass123");return r;}
 void await(CountDownLatch latch){try{assertTrue(latch.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
 void authenticateThenPause(CountDownLatch verified,CountDownLatch proceed){
  var provider=new DaoAuthenticationProvider();provider.setUserDetailsService(new CustomUserDetailsService(users));provider.setPasswordEncoder(encoder);
  doAnswer(call->{var result=provider.authenticate(call.getArgument(0));verified.countDown();await(proceed);return result;}).when(authentication).authenticate(any());
 }
 @ParameterizedTest @EnumSource(Role.class)
 void emailReuseNeverChangesAuthenticatedIdentity(Role role) throws Exception {
  var original=account(role);var input=request(original);var verified=new CountDownLatch(1);var proceed=new CountDownLatch(1);
  authenticateThenPause(verified,proceed);var login=executor.submit(()->auth.login(input));
  try{
   await(verified);new TransactionTemplate(manager).executeWithoutResult(tx->{var u=users.findForAssessmentLifecycle(original.getId()).orElseThrow();u.setEmail("changed-"+input.getEmail());users.saveAndFlush(u);});
   var replacement=account(Role.SUPER_ADMIN);replacement.setEmail(input.getEmail());users.saveAndFlush(replacement);
  }finally{proceed.countDown();}
  var response=login.get(15,TimeUnit.SECONDS);assertEquals(original.getId(),jwt.extractUserId(response.getToken()));
  assertEquals(role,response.getRole());assertEquals("changed-"+input.getEmail(),response.getEmail());
 }
 enum Change { PASSWORD, REVOKE, DEACTIVATE, ARCHIVE, BLOCK, NULL_STATUS, DELETE, ROLE }
 @ParameterizedTest @EnumSource(Change.class)
 void changesAfterPasswordVerificationPreventIssuance(Change change) throws Exception {
  var u=account(Role.ADMIN);var input=request(u);var verified=new CountDownLatch(1);var proceed=new CountDownLatch(1);
  authenticateThenPause(verified,proceed);var login=executor.submit(()->auth.login(input));
  try{await(verified);new TransactionTemplate(manager).executeWithoutResult(tx->{var current=users.findForAssessmentLifecycle(u.getId()).orElseThrow();
   switch(change){case PASSWORD->current.setPassword(encoder.encode("Replacement-pass123"));case REVOKE->current.setTokenVersion(1);case DEACTIVATE->current.setStatus(Status.INACTIVE);case ARCHIVE->current.setStatus(Status.ARCHIVED);case BLOCK->current.setStatus(Status.BLOCKED);case NULL_STATUS->current.setStatus(null);case DELETE->users.delete(current);case ROLE->current.setRole(Role.SUPER_ADMIN);}users.flush();
  });}finally{proceed.countDown();}
  var error=assertThrows(ExecutionException.class,()->login.get(15,TimeUnit.SECONDS));assertInstanceOf(BadCredentialsException.class,error.getCause());
  verify(jwt,never()).generateToken(any());verify(audit,never()).recordUser(eq(AuthEvent.LOGIN_SUCCESS),anyLong());
 }
 @ParameterizedTest @EnumSource(value=Change.class,names={"PASSWORD","REVOKE","DEACTIVATE"})
 void signingHoldsAccountLockAndLaterSecurityChangesRevokeIssuedToken(Change change) throws Exception {
  var u=account(Role.PROFESSOR);var input=request(u);
  var ready=new CountDownLatch(1);var sign=new CountDownLatch(1);var changing=new CountDownLatch(1);
  authenticateThenPause(new CountDownLatch(1),new CountDownLatch(0));
  doAnswer(call->{ready.countDown();await(sign);return call.callRealMethod();}).when(jwt).generateToken(any());
  var login=executor.submit(()->auth.login(input));
  Future<?> mutation=null;
  try {
   await(ready);
   mutation=executor.submit(()->new TransactionTemplate(manager).executeWithoutResult(tx->{
    changing.countDown();var current=users.findForAssessmentLifecycle(u.getId()).orElseThrow();
    if(change==Change.PASSWORD)current.setPassword(encoder.encode("Replacement-pass123"));
    if(change==Change.DEACTIVATE)current.setStatus(Status.INACTIVE);
    current.setTokenVersion(current.getTokenVersion()+1);users.flush();
   }));
   await(changing);var pending=mutation;
   assertThrows(TimeoutException.class,()->pending.get(250,TimeUnit.MILLISECONDS));
  } finally {sign.countDown();}
  var response=login.get(15,TimeUnit.SECONDS);mutation.get(15,TimeUnit.SECONDS);
  var current=users.findById(u.getId()).orElseThrow();
  assertEquals(u.getId(),jwt.extractUserId(response.getToken()));
  assertFalse(jwt.isCurrent(response.getToken(),new CustomUserDetails(current)));
  assertEquals(1,current.getTokenVersion());
 }
}
