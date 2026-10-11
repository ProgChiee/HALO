package com.ptc.halo;
import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.AuthController;
import com.ptc.halo.entity.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,PasswordResetService.class})
class PasswordResetPublicHttpTest {
 @Autowired MockMvc mvc;
 @MockBean AuthService auth;
 @MockBean UserRepository users;
 @MockBean PasswordResetOtpRepository otps;
 @MockBean EmailService email;
 @MockBean CustomUserDetailsService details;
 @MockBean JwtService jwt;
 UserEntity account(){var u=new UserEntity();u.setId(1L);u.setEmail("test@example.test");u.setPassword("encoded");return u;}
 @Test void forgotAndResendReachEmailWithoutJwt() throws Exception {
  when(users.findByEmail("test@example.test")).thenReturn(Optional.of(account()));
  when(otps.findByUserId(1L)).thenReturn(Optional.empty());
  for(String path:java.util.List.of("/forgot-password","/forgot-password/resend"))
   mvc.perform(post("/api/auth"+path).contentType("application/json").content("{\"email\":\"test@example.test\"}")).andExpect(status().isOk());
  verify(email,times(2)).sendPasswordResetOtp(eq("test@example.test"),anyString());
  verifyNoInteractions(jwt,details);
 }
 @Test void resetValidatesOtpWithoutJwt() throws Exception {
  when(users.findForPasswordChange("test@example.test")).thenReturn(Optional.of(account()));
  var otp=new PasswordResetOtpEntity();otp.setOtp("123456");otp.setExpiresAt(LocalDateTime.now().plusMinutes(10));
  when(otps.findByUserId(1L)).thenReturn(Optional.of(otp));
  mvc.perform(post("/api/auth/reset-password").contentType("application/json").content("{\"email\":\"test@example.test\",\"otp\":\"123456\",\"newPassword\":\"Replacement-123\"}")).andExpect(status().isOk());
  verify(users).save(any());verifyNoInteractions(jwt,details);
 }
 @Test void missingAccountBeforeSmtpIsSafe400Not401() throws Exception {
  mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{\"email\":\"missing@example.test\"}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESET_REQUEST"));
  verifyNoInteractions(email);
 }
 @Test void invalidOtpIsSafe400() throws Exception {
  when(users.findForPasswordChange("test@example.test")).thenReturn(Optional.of(account()));
  mvc.perform(post("/api/auth/reset-password").contentType("application/json").content("{\"email\":\"test@example.test\",\"otp\":\"123456\",\"newPassword\":\"Replacement-123\"}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESET_REQUEST"));
  verify(users,never()).save(any());
 }
 @Test void malformedAndInvalidInputsDoNotReachServiceRepositories() throws Exception {
  for(String path:java.util.List.of("/forgot-password","/forgot-password/resend","/reset-password"))
   for(String body:java.util.List.of("{}","{\"email\":\"invalid\"}","{"))
    mvc.perform(post("/api/auth"+path).contentType("application/json").content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").exists());
  verifyNoInteractions(users,otps,email,jwt,details);
 }
 @Test void unexpectedPreEmailFailureIsSafe500NotAuthError() throws Exception {
  when(users.findByEmail(anyString())).thenThrow(new IllegalStateException("private database detail"));
  mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{\"email\":\"test@example.test\"}"))
   .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("PASSWORD_RESET_FAILED"))
   .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private database detail"))));
 }
 @Test void protectedRoutesStillRequireJwt() throws Exception {
  for(String path:java.util.List.of("/api/admin/dashboard","/api/student/dashboard","/api/professor/dashboard","/api/super-admin/dashboard"))
   mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  mvc.perform(post("/api/auth/change-password").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
  verifyNoInteractions(users,email,otps);
 }
}
