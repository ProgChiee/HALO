package com.ptc.halo;

import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import com.ptc.halo.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={
 "spring.datasource.url=jdbc:h2:mem:resetsecurity;MODE=MariaDB;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
 "halo.password-reset.pepper=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({PasswordResetService.class,PasswordResetProtection.class,PasswordResetThrottle.class,PasswordResetSecurityTest.Config.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class PasswordResetSecurityTest {
 @TestConfiguration static class Config { @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder(4);} }
 @Autowired PasswordResetService service; @Autowired PasswordResetProtection protection; @Autowired PasswordResetThrottle throttle;
 @Autowired UserRepository users; @Autowired PasswordResetOtpRepository otps; @Autowired JdbcTemplate jdbc;
 @Autowired PasswordEncoder encoder; @Autowired PlatformTransactionManager manager;
 @MockBean EmailService mail; @MockBean AuthAuditService audit;
 UserEntity user; AtomicReference<String> delivered=new AtomicReference<>();
 @BeforeEach void setup(){
  reset(mail,audit);otps.deleteAll();users.deleteAll();jdbc.update("delete from password_reset_rate_limits");
  user=new UserEntity();user.setEmail("student@example.test");user.setName("Student");user.setRole(Role.STUDENT);user.setStatus(Status.ACTIVE);user.setPassword(encoder.encode("old-password"));user=users.saveAndFlush(user);
  doAnswer(call->{delivered.set(call.getArgument(1));return null;}).when(mail).sendPasswordResetOtp(anyString(),anyString());
 }
 void issue(){service.sendOtp(user.getEmail(),"192.0.2.1");}
 PasswordResetOtpEntity challenge(){return otps.findByUserId(user.getId()).orElseThrow();}
 String wrong(){return delivered.get().equals("000000")?"000001":"000000";}
 void submitCode(String code){service.resetPassword(user.getEmail(),code,"new-password","192.0.2.1");}
 void allowResend(){jdbc.update("update password_reset_rate_limits set next_allowed_at=null");}
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(ints={71,72,73})
 void utf8BoundaryPreservesOtpOnInvalidPassword(int bytes){
  issue();String code=delivered.get();String password="é".repeat(35)+"a".repeat(bytes-70);
  String old=users.findById(user.getId()).orElseThrow().getPassword();
  if(bytes>72){
   var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.resetPassword(user.getEmail(),code,password,"192.0.2.1"));
   assertEquals(400,error.getStatusCode().value());assertEquals("INVALID_NEW_PASSWORD",error.getReason());
   assertEquals(old,users.findById(user.getId()).orElseThrow().getPassword());assertEquals(0,challenge().getFailedAttempts());
   assertTrue(protection.matches(user.getId(),code,challenge().getOtp()));submitCode(code);
  }else{
   service.resetPassword(user.getEmail(),code,password,"192.0.2.1");
   assertTrue(encoder.matches(password,users.findById(user.getId()).orElseThrow().getPassword()));
   assertTrue(otps.findByUserId(user.getId()).isEmpty());
  }
 }
 @Test void validSingleUseResetRevokesJwtAndStoresNoPlaintext(){
  var jwt=new JwtService("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");String token=jwt.generateToken(user);
  issue();String code=delivered.get();assertTrue(challenge().getOtp().startsWith("v1:"));assertNotEquals(code,challenge().getOtp());
  assertTrue(protection.matches(user.getId(),code,challenge().getOtp()));assertFalse(protection.matches(user.getId()+1,code,challenge().getOtp()));
  submitCode(code);var saved=users.findById(user.getId()).orElseThrow();assertTrue(encoder.matches("new-password",saved.getPassword()));
  assertFalse(jwt.isCurrent(token,new CustomUserDetails(saved)));assertTrue(otps.findByUserId(user.getId()).isEmpty());
  assertThrows(PasswordResetException.class,()->submitCode(code));assertEquals(1,saved.getTokenVersion());
  assertEquals(Role.STUDENT,saved.getRole());assertEquals(Status.ACTIVE,saved.getStatus());
 }
 @Test void belowLimitFailuresCommitAndCorrectCodeStillWorks(){issue();for(int i=1;i<5;i++){assertEquals(400,assertThrows(PasswordResetException.class,()->submitCode(wrong())).getStatus());assertEquals(i,challenge().getFailedAttempts());}submitCode(delivered.get());}
 @Test void incorrectGuessesMustEventuallyInvalidateChallenge(){issue();for(int i=0;i<5;i++)assertThrows(PasswordResetException.class,()->submitCode(wrong()));assertEquals(5,challenge().getFailedAttempts());assertThrows(PasswordResetException.class,()->submitCode(delivered.get()));assertEquals(0,users.findById(user.getId()).orElseThrow().getTokenVersion());}
 @Test void expiredAndLegacyPlaintextCodesCannotBeUsed(){
  issue();jdbc.update("update password_reset_otps set expires_at=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)");assertThrows(PasswordResetException.class,()->submitCode(delivered.get()));
  jdbc.update("update password_reset_otps set expires_at=DATEADD('MINUTE',10,CURRENT_TIMESTAMP),otp='123456'");assertThrows(PasswordResetException.class,()->submitCode("123456"));
 }
 @Test void immediateResendMustNotDeliverAnotherEmail(){issue();var failure=assertThrows(PasswordResetException.class,()->service.sendOtp(user.getEmail(),"192.0.2.2"));assertEquals(429,failure.getStatus());assertTrue(failure.getRetryAfterSeconds()>0);verify(mail,times(1)).sendPasswordResetOtp(anyString(),anyString());}
 @Test void resendKeepsFailureCountAndOriginalExpiryAndCannotReplaceLockedChallenge(){
  issue();assertThrows(PasswordResetException.class,()->submitCode(wrong()));String old=delivered.get();var expiry=challenge().getExpiresAt();allowResend();issue();
  assertEquals(1,challenge().getFailedAttempts());assertEquals(expiry,challenge().getExpiresAt());
  if(!old.equals(delivered.get()))assertThrows(PasswordResetException.class,()->submitCode(old));
  while(challenge().getFailedAttempts()<5)assertThrows(PasswordResetException.class,()->submitCode(wrong()));
  String locked=challenge().getOtp();allowResend();issue();assertEquals(locked,challenge().getOtp());verify(mail,times(2)).sendPasswordResetOtp(anyString(),anyString());
 }
 @Test void accountLimitSurvivesNewChallengesAndDifferentIps(){
  for(int i=0;i<3;i++){service.sendOtp(" STUDENT@EXAMPLE.TEST ","192.0.2."+i);jdbc.update("update password_reset_otps set expires_at=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP)");allowResend();}
  assertEquals(429,assertThrows(PasswordResetException.class,()->service.sendOtp(user.getEmail(),"198.51.100.1")).getStatus());
 }
 @Test void unknownAndKnownIdentifiersShareRequestAndVerificationLimits(){
  service.sendOtp("missing@example.test","192.0.2.2");assertEquals(429,assertThrows(PasswordResetException.class,()->service.sendOtp(" MISSING@EXAMPLE.TEST ","192.0.2.3")).getStatus());verifyNoInteractions(mail);
  for(int i=0;i<10;i++)assertEquals(400,assertThrows(PasswordResetException.class,()->service.resetPassword("missing@example.test","000000","new-password","198.51.100.1")).getStatus());
  assertEquals(429,assertThrows(PasswordResetException.class,()->service.resetPassword("missing@example.test","000000","new-password","198.51.100.2")).getStatus());
 }
 @Test void ipLimitsDifferentAccountsAndRejectedAccountRequestsStillConsumeIpAllowance(){
  for(int i=0;i<20;i++)service.sendOtp("missing"+i+"@example.test","192.0.2.1");
  assertEquals(429,assertThrows(PasswordResetException.class,()->service.sendOtp("another@example.test","192.0.2.1")).getStatus());
  for(int i=0;i<100;i++){final int n=i;assertEquals(400,assertThrows(PasswordResetException.class,()->service.resetPassword("missing"+n+"@example.test","000000","new-password","192.0.2.1")).getStatus());}
  assertEquals(429,assertThrows(PasswordResetException.class,()->service.resetPassword("another@example.test","000000","new-password","192.0.2.1")).getStatus());
 }
 List<Integer> race(int count,Callable<Integer> action)throws Exception{
  var pool=Executors.newFixedThreadPool(count);var gate=new CountDownLatch(1);
  try{var futures=new ArrayList<Future<Integer>>();for(int i=0;i<count;i++)futures.add(pool.submit(()->{gate.await();return action.call();}));gate.countDown();var values=new ArrayList<Integer>();for(var f:futures)values.add(f.get(20,TimeUnit.SECONDS));return values;}finally{pool.shutdownNow();}
 }
 @Test void concurrentIncorrectRequestsCommitExactlyFiveFailures()throws Exception{issue();var values=race(8,()->{try{submitCode(wrong());return 200;}catch(PasswordResetException e){return e.getStatus();}});assertTrue(values.stream().allMatch(s->s==400));assertEquals(5,challenge().getFailedAttempts());assertThrows(PasswordResetException.class,()->submitCode(delivered.get()));}
 @Test void concurrentSuccessConsumesCodeOnce()throws Exception{issue();String code=delivered.get();var values=race(2,()->{try{submitCode(code);return 200;}catch(PasswordResetException e){return e.getStatus();}});assertEquals(1,values.stream().filter(s->s==200).count());assertEquals(1,values.stream().filter(s->s==400).count());assertEquals(1,users.findById(user.getId()).orElseThrow().getTokenVersion());}
 @Test void concurrentIssuanceDeliversOnce()throws Exception{var values=race(6,()->{try{issue();return 200;}catch(PasswordResetException e){return e.getStatus();}});assertEquals(1,values.stream().filter(s->s==200).count());verify(mail,times(1)).sendPasswordResetOtp(anyString(),anyString());}
 @Test void admissionSurvivesMailFailureAndServiceRecreation(){doThrow(new IllegalStateException("SMTP private detail")).when(mail).sendPasswordResetOtp(anyString(),anyString());issue();assertTrue(otps.findByUserId(user.getId()).isPresent());var other=new PasswordResetThrottle(jdbc,protection,manager);assertEquals(429,assertThrows(PasswordResetException.class,()->other.admit(user.getEmail(),"192.0.2.2",false)).getStatus());verifyNoInteractions(audit);}
 @Test void policyIsConfigurableAndAllRealRolesUseSameRecovery(){
  var three=new PasswordResetProtection("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",3,600,60,3600,3,20,10,100);
  var limited=new PasswordResetService(otps,users,mail,encoder,audit,three,throttle,manager);issue();
  for(int i=0;i<3;i++)assertThrows(PasswordResetException.class,()->limited.resetPassword(user.getEmail(),wrong(),"new-password","192.0.2.1"));
  assertThrows(PasswordResetException.class,()->limited.resetPassword(user.getEmail(),delivered.get(),"new-password","192.0.2.1"));
  for(var role:List.of(Role.STUDENT,Role.PROFESSOR,Role.ADMIN,Role.SUPER_ADMIN)){otps.deleteAll();jdbc.update("delete from password_reset_rate_limits");user.setRole(role);user.setPassword(encoder.encode("old-password"));user=users.saveAndFlush(user);issue();submitCode(delivered.get());assertEquals(role,users.findById(user.getId()).orElseThrow().getRole());}
 }
 @Test void expiredRateWindowsPermitRecovery(){issue();jdbc.update("update password_reset_rate_limits set window_started_at=DATEADD('HOUR',-2,CURRENT_TIMESTAMP),next_allowed_at=null");issue();verify(mail,times(2)).sendPasswordResetOtp(anyString(),anyString());}
 @Test void databaseEquivalentEmailSpellingsCannotSplitAccountBuckets(){
  issue();var aliases=mock(UserRepository.class);when(aliases.findByEmail("alias@example.test")).thenReturn(Optional.of(user));
  var alternate=new PasswordResetService(otps,aliases,mail,encoder,audit,protection,throttle,manager);
  assertEquals(429,assertThrows(PasswordResetException.class,()->alternate.sendOtp("alias@example.test","198.51.100.2")).getStatus());
  verify(mail,times(1)).sendPasswordResetOtp(anyString(),anyString());
 }
}
