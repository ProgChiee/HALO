package com.ptc.halo;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import com.ptc.halo.security.JwtService;
import com.ptc.halo.dtoRequest.LoginRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","halo.password-reset.pepper=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="},showSql=false)
@Import({AuthService.class, com.ptc.halo.service.LoginTokenIssuer.class,AuthAuditService.class,ActivityLogService.class,PasswordResetService.class,PasswordResetProtection.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AuthenticationAuditTest {
 @Autowired AuthService auth; @Autowired AuthAuditService audit; @Autowired PasswordResetService reset;
 @Autowired ActivityLogService activity; @Autowired ActivityLogRepository logs; @Autowired UserRepository users; @Autowired PasswordResetOtpRepository otps;
 @MockBean PasswordResetThrottle throttle;
 @MockBean AuthenticationManager authentication; @MockBean JwtService jwt; @MockBean PasswordEncoder encoder; @MockBean EmailService mail;
 @SpyBean ActivityLogService writer;
 UserEntity student;
 @BeforeEach void setup(){reset(org.springframework.test.util.AopTestUtils.getUltimateTargetObject(writer),authentication,jwt,encoder,mail);logs.deleteAll();otps.deleteAll();users.deleteAll();student=new UserEntity();student.setName("Archie");student.setEmail("archie@example.test");student.setRole(Role.STUDENT);student.setStatus(Status.ACTIVE);student.setPassword("stored-hash-secret");student=users.saveAndFlush(student);when(authentication.authenticate(any())).thenReturn(UsernamePasswordAuthenticationToken.authenticated(new com.ptc.halo.security.CustomUserDetails(student), null, new com.ptc.halo.security.CustomUserDetails(student).getAuthorities()));when(throttle.databaseNow()).thenAnswer(call->java.time.LocalDateTime.now());when(jwt.generateToken(any())).thenReturn("jwt-secret");when(encoder.encode(any())).thenReturn("new-hash-secret");when(encoder.matches(eq("password-secret"),any())).thenReturn(true);}
 LoginRequest request(String email){var r=new LoginRequest();r.setEmail(email);r.setPassword("password-secret");return r;}
 @Test void successAndFailureAreSafeAndUnknownUsersAppearInAuthFilter(){
  assertEquals("jwt-secret",auth.login(request(student.getEmail())).getToken());
  when(authentication.authenticate(any())).thenThrow(new BadCredentialsException("Invalid credentials"));
  assertThrows(BadCredentialsException.class,()->auth.login(request(student.getEmail())));
  assertThrows(BadCredentialsException.class,()->auth.login(request("unknown@example.test")));
  var authRows=activity.getLogs(null,ActivityType.AUTH,0,20,"");assertEquals(3,authRows.getTotalElements());assertTrue(authRows.stream().allMatch(r->r.getActivityType()==ActivityType.AUTH));
  assertTrue(authRows.stream().anyMatch(r->r.getDisplayAction().equals("Archie logged in")));
  assertTrue(authRows.stream().anyMatch(r->r.getDisplayAction().equals("Failed login attempt for unknown@example.test")));
  assertEquals(1,logs.findAll().stream().filter(l->l.getUser()==null).count());
  assertEquals(3,activity.getLogs(null,null,0,20,"").getTotalElements());assertEquals(0,activity.getLogs(null,ActivityType.ACCOUNT,0,20,"").getTotalElements());
  assertEquals(1,activity.getLogs(null,ActivityType.AUTH,0,20,"logged in").getTotalElements());
  assertEquals(2,activity.getLogs(null,ActivityType.AUTH,0,20,"archie@example.test").getTotalElements());
  for(var r:authRows){assertFalse(r.getAction().contains("password-secret"));assertFalse(r.getAction().contains("jwt-secret"));assertFalse(r.getAction().contains("stored-hash-secret"));}
 }
 @Test void accountStatesAreLoggedWithoutChangingErrors(){
  when(authentication.authenticate(any())).thenThrow(new DisabledException("disabled"));
  for(var status:java.util.List.of(Status.INACTIVE,Status.ARCHIVED,Status.BLOCKED)){
   student.setStatus(status);users.saveAndFlush(student);assertThrows(AccountStateException.class,()->auth.login(request(student.getEmail())));
  }
  assertEquals(java.util.Set.of(AuthEvent.ACCOUNT_INACTIVE_LOGIN_ATTEMPT,AuthEvent.ACCOUNT_ARCHIVED_LOGIN_ATTEMPT,AuthEvent.ACCOUNT_BLOCKED_LOGIN_ATTEMPT),logs.findAll().stream().map(ActivityLogEntity::getAuthEvent).collect(java.util.stream.Collectors.toSet()));
 }
 @Test void logoutIsDurablyDeduplicatedWithoutStoringToken(){
  audit.logout(student.getEmail(),"Bearer highly-secret-token");audit.logout(student.getEmail(),"Bearer highly-secret-token");
  assertEquals(1,logs.count());var l=logs.findAll().get(0);assertEquals(AuthEvent.LOGOUT,l.getAuthEvent());assertEquals(64,l.getAuthEventKey().length());assertFalse(l.getAction().contains("token"));assertEquals("Archie logged out",l.getAuthDisplay());
 }
 @Test void resetRecordsOnlyCommittedSafeEvents(){
  reset.sendOtp(student.getEmail());assertEquals(1,logs.count());assertEquals(AuthEvent.PASSWORD_RESET_REQUESTED,logs.findAll().get(0).getAuthEvent());
  var delivery=org.mockito.ArgumentCaptor.forClass(String.class);verify(mail).sendPasswordResetOtp(eq(student.getEmail()),delivery.capture());
  String otp=delivery.getValue();assertNotEquals(otp,otps.findByUserId(student.getId()).orElseThrow().getOtp());
  reset.resetPassword(student.getEmail(),otp,"new-password-secret");
  assertEquals(3,logs.count());assertTrue(otps.findByUserId(student.getId()).isEmpty());
  assertTrue(logs.findAll().stream().anyMatch(l->l.getAuthEvent()==AuthEvent.OTP_VERIFIED));assertTrue(logs.findAll().stream().anyMatch(l->l.getAuthEvent()==AuthEvent.PASSWORD_RESET_COMPLETED));
  for(var l:logs.findAll()){assertFalse(l.getAction().contains(otp));assertFalse(l.getAuthDisplay().contains(otp));assertFalse(l.getAction().contains("new-password-secret"));}
 }
 @Test void snapshotSurvivesNameChangesAndResponseDoesNotExposeDedupeKey() throws Exception {
  audit.logout(student.getEmail(),"Bearer secret-token");student.setName("Changed name");users.saveAndFlush(student);
  var rows=activity.getLogs(null,ActivityType.AUTH,0,20,"");assertEquals("Archie logged out",rows.getContent().get(0).getDisplayAction());
  var json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(rows.getContent());
  assertFalse(json.contains("authEventKey"));assertFalse(json.contains("secret-token"));assertFalse(json.contains("stored-hash-secret"));
 }
 @Test void failedResetDoesNotClaimCompletion(){
  doThrow(new IllegalStateException("mail unavailable")).when(mail).sendPasswordResetOtp(anyString(),anyString());
  assertDoesNotThrow(()->reset.sendOtp(student.getEmail()));assertEquals(0,logs.count());
  assertThrows(PasswordResetException.class,()->reset.resetPassword(student.getEmail(),"000000","replacement-password"));assertEquals(0,logs.count());
 }
 @Test void logoutRevocationCommitsEvenWhenAuditStorageFails() {
  when(jwt.isCurrent(anyString(),any())).thenReturn(true);
  doThrow(new IllegalStateException("audit unavailable")).when(org.springframework.test.util.AopTestUtils.<ActivityLogService>getUltimateTargetObject(writer)).createAuthLog(any(),any(),any(),any());
  var identity=TestIdentity.auth(student);
  auth.logout(identity,"Bearer test-only-token");
  assertEquals(1,users.findById(student.getId()).orElseThrow().getTokenVersion());assertEquals(0,logs.count());
 }
 @Test void auditFailureDoesNotBreakSuccessfulLogin(){
  doThrow(new IllegalStateException("audit unavailable")).when(org.springframework.test.util.AopTestUtils.<ActivityLogService>getUltimateTargetObject(writer)).createAuthLog(any(),any(),any(),any());
  assertEquals("jwt-secret",auth.login(request(student.getEmail())).getToken());
 }
}
