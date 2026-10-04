package com.ptc.halo;
import com.ptc.halo.component.SuperAdminInitializer;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import com.ptc.halo.dtoRequest.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AdminForcedPasswordTest {
 String key() { return Base64.getEncoder().encodeToString(new byte[32]); }
 UserEntity account() { var u = new UserEntity(); u.setEmail("admin@example.test"); u.setName("Admin"); u.setRole(Role.ADMIN); u.setStatus(Status.ACTIVE); return u; }
    @Test void passwordChangeRevokesOldTokenAndNewLoginWorks() {
        var users = mock(UserRepository.class); var encoder = new BCryptPasswordEncoder();
        var user = account(); user.setMustChangePassword(true); user.setPassword(encoder.encode("Temporary-test-123"));
        when(users.findForPasswordChange(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var jwt = new JwtService(key()); var old = jwt.generateToken(user);
        var service = new PasswordResetService(mock(PasswordResetOtpRepository.class), users, mock(EmailService.class), encoder);
        service.changePassword(user, "Temporary-test-123", "Replacement-test-123");
        assertFalse(jwt.isCurrent(old, new CustomUserDetails(user))); assertFalse(user.isMustChangePassword());
        assertTrue(encoder.matches("Replacement-test-123", user.getPassword()));
        var details = mock(CustomUserDetailsService.class);
        when(details.loadUserByUsername(user.getEmail())).thenReturn(new CustomUserDetails(user));
        var provider = new org.springframework.security.authentication.dao.DaoAuthenticationProvider();
        provider.setUserDetailsService(details); provider.setPasswordEncoder(encoder);
        AuthenticationManager manager = new org.springframework.security.authentication.ProviderManager(provider);
        var auth = new AuthService(users, mock(StudentProfileRepository.class), encoder, manager, jwt);
        var request = new LoginRequest(); request.setEmail(user.getEmail()); request.setPassword("Replacement-test-123");
        var response = auth.login(request);
        assertTrue(jwt.isCurrent(response.getToken(), new CustomUserDetails(user)));
    }

 @Test void flaggedRolesCanOnlyUseOwnProfileAndPasswordChange() throws Exception {
  for (Role role : List.of(Role.ADMIN, Role.SUPER_ADMIN)) {
   var user=account(); user.setRole(role); user.setMustChangePassword(true);
   var jwt=new JwtService(key()); var details=mock(CustomUserDetailsService.class);
   when(details.loadUserByUsername(user.getEmail())).thenReturn(new CustomUserDetails(user));
   var filter=new JwtAuthenticationFilter(jwt,details);
   String own=role==Role.ADMIN?"/api/admin/profile":"/api/super-admin/profile";
   for(String path:List.of("/api/admin/profile","/api/super-admin/profile","/api/admin/dashboard","/api/admin/professors","/api/super-admin/admins","/api/auth/change-password")) {
    for(String method:List.of("GET","POST")) {
     SecurityContextHolder.clearContext();
     var req=new MockHttpServletRequest(method,path);req.setServletPath(path);req.addHeader("Authorization","Bearer "+jwt.generateToken(user));
     var res=new MockHttpServletResponse();var chain=new MockFilterChain();
     try {
      filter.doFilter(req,res,chain);
      boolean allowed=(method.equals("GET")&&path.equals(own))||(method.equals("POST")&&path.equals("/api/auth/change-password"));
      assertEquals(allowed?200:403,res.getStatus(),method+" "+path+" "+role);
      if(allowed) assertNotNull(chain.getRequest()); else {assertNull(chain.getRequest());assertTrue(res.getContentAsString().contains("PASSWORD_CHANGE_REQUIRED"));}
     } finally {SecurityContextHolder.clearContext();}
    }
   }
  }
 }
}
