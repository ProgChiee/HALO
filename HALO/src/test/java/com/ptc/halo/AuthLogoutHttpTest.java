package com.ptc.halo;
import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.AuthController;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import com.ptc.halo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class AuthLogoutHttpTest {
 @Autowired MockMvc mvc;
 @MockBean AuthService auth; @MockBean PasswordResetService reset; @MockBean UserRepository users; @MockBean JwtService jwt; @MockBean CustomUserDetailsService details;
 @Test void logoutRequiresAuthentication() throws Exception {mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());verifyNoInteractions(auth);}
 @Test void allRealRolesReportTheAuthenticatedActor() throws Exception {
  for(String role:java.util.List.of("ADMIN","SUPER_ADMIN","PROFESSOR","STUDENT")){
   when(jwt.extractUsername("test-token")).thenReturn("actor@example.test");when(jwt.isCurrent(eq("test-token"),any())).thenReturn(true);
   when(details.loadUserByUsername("actor@example.test")).thenReturn(User.withUsername("actor@example.test").password("unused").roles(role).build());
   mvc.perform(post("/api/auth/logout").header("Authorization","Bearer test-token").header("X-Acting-Session","ignored-target-context")).andExpect(status().isNoContent());
  }
  verify(auth,times(4)).logout(argThat(a->a.getName().equals("actor@example.test")),eq("Bearer test-token"));
 }
}
