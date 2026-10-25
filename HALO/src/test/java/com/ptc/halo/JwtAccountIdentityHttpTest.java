package com.ptc.halo;

import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.AuthController;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={AuthController.class,JwtAccountIdentityHttpTest.IdentityController.class},
 properties="jwt.secret=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=")
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,JwtService.class,CustomUserDetailsService.class,AuthService.class,JwtAccountIdentityHttpTest.IdentityController.class})
class JwtAccountIdentityHttpTest {
 @RestController static class IdentityController {
  @GetMapping({"/api/student/identity-test","/api/professor/identity-test","/api/admin/identity-test","/api/super-admin/identity-test"})
  public Map<String,Object> identity(Authentication auth) {
   var account=(CustomUserDetails)auth.getPrincipal();
   return Map.of("id",account.getUserId(),"email",account.getUsername());
  }
 }
 @Autowired MockMvc mvc; @Autowired JwtService jwt; @Autowired PasswordEncoder encoder;
 @MockBean UserRepository users; @MockBean StudentProfileRepository profiles;
 @MockBean PasswordResetService resets; @MockBean AuthAuditService audit;
 @MockBean LoginTokenIssuer tokenIssuer;
 Map<Long,UserEntity> accounts;
 @BeforeEach void setup(){
  accounts=new HashMap<>();
  var entityManager = mock(jakarta.persistence.EntityManager.class);
  var issuer = new LoginTokenIssuer(jwt);
  org.springframework.test.util.ReflectionTestUtils.setField(issuer, "entityManager", entityManager);
  when(tokenIssuer.issue(anyLong(), anyString(), anyLong(), anySet())).thenAnswer(call -> issuer.issue(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
  when(entityManager.find(eq(UserEntity.class), anyLong(), eq(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE))).thenAnswer(call -> accounts.get(call.getArgument(1)));
  when(users.findById(anyLong())).thenAnswer(call->Optional.ofNullable(accounts.get(call.getArgument(0))));
  when(users.findByEmail(anyString())).thenAnswer(call->accounts.values().stream().filter(u->u.getEmail().equals(call.getArgument(0))).findFirst());
 }
 UserEntity account(long id,Role role){
  var u=new UserEntity();u.setId(id);u.setName("Synthetic account");u.setEmail("account"+id+"@example.test");
  u.setRole(role);u.setStatus(Status.ACTIVE);u.setPassword(encoder.encode("Synthetic-password123"));accounts.put(id,u);return u;
 }
 String path(Role role){return "/api/"+(role==Role.SUPER_ADMIN?"super-admin":role.name().toLowerCase(Locale.ROOT))+"/identity-test";}
 @ParameterizedTest @EnumSource(Role.class)
 void loginAndAuthorizationUseRealJwtForEveryRole(Role role) throws Exception {
  var u=account(1,role);
  var response=mvc.perform(post("/api/auth/login").contentType("application/json")
   .content("{\"email\":\""+u.getEmail()+"\",\"password\":\"Synthetic-password123\"}"))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  String token=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("token").asText();
  assertEquals(1L,jwt.extractUserId(token));
  mvc.perform(get(path(role)).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
  for(Role other:Role.values())if(other!=role)mvc.perform(get(path(other)).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
 }
 @Test void emailReuseCannotTransferTokenAndDeletedOriginalFailsClosed() throws Exception {
  var original=account(1,Role.ADMIN);String oldEmail=original.getEmail();String token=jwt.generateToken(original);
  original.setEmail("changed@example.test");var replacement=account(2,Role.ADMIN);replacement.setEmail(oldEmail);
  assertFalse(jwt.isCurrent(token,new CustomUserDetails(replacement)));
  mvc.perform(get(path(Role.ADMIN)).header("Authorization","Bearer "+token)).andExpect(status().isOk())
   .andExpect(jsonPath("$.id").value(1)).andExpect(jsonPath("$.email").value("changed@example.test"));
  accounts.remove(1L);
  mvc.perform(get(path(Role.ADMIN)).header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
 }
 @Test void tokenVersionMismatchStillRevokesToken() throws Exception {
  var u=account(1,Role.ADMIN);String token=jwt.generateToken(u);u.setTokenVersion(1);
  mvc.perform(get(path(Role.ADMIN)).header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
  mvc.perform(get(path(Role.ADMIN)).header("Authorization","Bearer "+jwt.generateToken(u))).andExpect(status().isOk());
 }
 @ParameterizedTest @EnumSource(value=Status.class,names={"INACTIVE","ARCHIVED","BLOCKED"})
 void disabledAccountsRemainBlocked(Status state) throws Exception {
  var u=account(1,Role.ADMIN);String token=jwt.generateToken(u);u.setStatus(state);
  mvc.perform(get(path(Role.ADMIN)).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
 }
 @ParameterizedTest @EnumSource(Role.class)
 void missingStatusFailsClosedForLoginAndExistingTokens(Role role) throws Exception {
  var u=account(1,role);String token=jwt.generateToken(u);u.setStatus(null);
  for(String password:List.of("Synthetic-password123","Wrong-password123")) {
   mvc.perform(post("/api/auth/login").contentType("application/json")
    .content("{\"email\":\""+u.getEmail()+"\",\"password\":\""+password+"\"}"))
    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.token").doesNotExist());
  }
  mvc.perform(get(path(role)).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
  assertNull(u.getStatus());
  verify(audit,never()).record(eq(AuthEvent.LOGIN_SUCCESS),anyString());
  verify(audit).record(AuthEvent.ACCOUNT_LOGIN_BLOCKED,u.getEmail());
 }
 @ParameterizedTest @EnumSource(Role.class)
 void nonActiveStatesCannotLoginForAnyRole(Role role) throws Exception {
  var u=account(1,role);
  for(Status state:List.of(Status.INACTIVE,Status.ARCHIVED,Status.BLOCKED)) {
   u.setStatus(state);
   mvc.perform(post("/api/auth/login").contentType("application/json")
    .content("{\"email\":\""+u.getEmail()+"\",\"password\":\"Synthetic-password123\"}"))
    .andExpect(status().isForbidden()).andExpect(jsonPath("$.token").doesNotExist())
    .andExpect(jsonPath("$.code").value(state==Status.ARCHIVED?"ACCOUNT_ARCHIVED":"ACCOUNT_INACTIVE"));
  }
  verify(audit,never()).record(eq(AuthEvent.LOGIN_SUCCESS),anyString());
 }
 String signed(String subject,String format,Date expiration){
  var builder=io.jsonwebtoken.Jwts.builder().subject(subject).claim("ver",0);
  if(format!=null)builder.claim("identity",format);
  if(expiration!=null)builder.expiration(expiration);
  return builder.signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(Base64.getDecoder().decode("YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="))).compact();
 }
 @Test void legacyMalformedExpiredAndUnmarkedNumericTokensAreRejectedBeforeLookup() throws Exception {
  var u=account(1,Role.ADMIN);var future=new Date(System.currentTimeMillis()+60000);
  for(String token:List.of(signed(u.getEmail(),null,future),signed("1",null,future),signed("1","user-id-v1",new Date(1)),
    signed("0","user-id-v1",future),signed("999999999999999999999999","user-id-v1",future),signed("1","unknown",future),
    signed("1","user-id-v1",null),"broken.token")){
   mvc.perform(get(path(Role.ADMIN)).header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized())
    .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
  }
  verify(users,never()).findById(anyLong());verify(users,never()).findByEmail(anyString());
 }
}
