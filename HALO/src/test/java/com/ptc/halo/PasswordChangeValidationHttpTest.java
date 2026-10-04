package com.ptc.halo;
import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.AuthController;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.verifyNoInteractions;
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class PasswordChangeValidationHttpTest {
 @Autowired MockMvc mvc;
 @MockBean AuthService auth;
 @MockBean PasswordResetService passwords;
 @MockBean UserRepository users;
 @MockBean CustomUserDetailsService details;
 @MockBean JwtService jwt;
 @Test void missingOrShortPasswordsReturnStructured400WithoutCallingPasswordService() throws Exception {
  for (String body : java.util.List.of("{}", "{\"currentPassword\":\"current\",\"newPassword\":\"short\"}"))
   mvc.perform(post("/api/auth/change-password").with(user("test@example.test").roles("SUPER_ADMIN"))
    .contentType("application/json").content(body)).andExpect(status().isBadRequest())
    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.errors").isMap());
  verifyNoInteractions(passwords);
 }
}
