package com.ptc.halo;

import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.*;
import org.springframework.transaction.annotation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="},showSql=false)
@Import({AuthService.class, com.ptc.halo.service.LoginTokenIssuer.class,JwtService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class LogoutRevocationTest {
 @Autowired AuthService auth; @Autowired JwtService jwt; @Autowired UserRepository users;
 @MockBean AuthenticationManager manager; @MockBean PasswordEncoder encoder; @MockBean AuthAuditService audit;
 @ParameterizedTest @EnumSource(Role.class)
 void committedLogoutRejectsTokenReplayAndAllowsFreshToken(Role role) throws Exception {
  var user=new UserEntity();user.setName("Logout account");user.setEmail(role+"@logout.test");user.setRole(role);user.setStatus(Status.ACTIVE);user=users.saveAndFlush(user);
  String token=jwt.generateToken(user);String email=user.getEmail();Long id=user.getId();
  var details=mock(CustomUserDetailsService.class);
  when(details.loadUserById(id)).thenAnswer(call->new CustomUserDetails(users.findById(id).orElseThrow()));
  var filter=new JwtAuthenticationFilter(jwt,details);
  assertEquals(200,request(filter,token));
  var identity=TestIdentity.auth(user);
  auth.logout(identity,"Bearer "+token);
  assertEquals(1,users.findById(id).orElseThrow().getTokenVersion());
  assertEquals(401,request(filter,token));
  assertThrows(BadCredentialsException.class,()->auth.logout(identity,"Bearer "+token));
  assertEquals(1,users.findById(id).orElseThrow().getTokenVersion());
  assertEquals(200,request(filter,jwt.generateToken(users.findById(id).orElseThrow())));
  verify(audit,times(1)).logoutUser(id,"Bearer "+token);
 }
 private int request(JwtAuthenticationFilter filter,String token) throws Exception {
  SecurityContextHolder.clearContext();
  try {
   var request=new MockHttpServletRequest("GET","/api/profile");request.setServletPath("/api/profile");request.addHeader("Authorization","Bearer "+token);
   var response=new MockHttpServletResponse();var chain=new MockFilterChain();filter.doFilter(request,response,chain);
   if(response.getStatus()==401)assertNull(chain.getRequest());else assertNotNull(SecurityContextHolder.getContext().getAuthentication());
   return response.getStatus();
  } finally {SecurityContextHolder.clearContext();}
 }
}
