package com.ptc.halo;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import com.ptc.halo.security.*;
import com.ptc.halo.dtoRequest.LoginRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
class AccountStateLoginTest {
 @Test void inactiveAndArchivedCannotLoginAndWrongPasswordDoesNotRevealState() {
  var users=mock(UserRepository.class); var encoder=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4);
  var target=new UserEntity();target.setEmail("student@example.test");target.setRole(Role.STUDENT);target.setPassword(encoder.encode("secret-password"));
  when(users.findByEmail(target.getEmail())).thenReturn(Optional.of(target));
  var provider=new org.springframework.security.authentication.dao.DaoAuthenticationProvider();provider.setUserDetailsService(new CustomUserDetailsService(users));provider.setPasswordEncoder(encoder);
  var jwt=mock(JwtService.class);
  var auth=new AuthService(users,mock(StudentProfileRepository.class),encoder,new org.springframework.security.authentication.ProviderManager(provider),jwt,mock(com.ptc.halo.service.AuthAuditService.class));
  var request=new LoginRequest();request.setEmail(target.getEmail());request.setPassword("secret-password");
  for(Status state:new Status[]{Status.INACTIVE,Status.ARCHIVED}) {
   target.setStatus(state);
   assertEquals(state==Status.ARCHIVED?"ACCOUNT_ARCHIVED":"ACCOUNT_INACTIVE",assertThrows(AccountStateException.class,()->auth.login(request)).code);
  }
  request.setPassword("wrong");assertThrows(org.springframework.security.authentication.BadCredentialsException.class,()->auth.login(request));
  verifyNoInteractions(jwt);
 }
}
