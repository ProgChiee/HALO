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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class PasswordResetPublicHttpTest {
 @Autowired MockMvc mvc;
 @MockBean AuthService auth; @MockBean PasswordResetService resets; @MockBean UserRepository users;
 @MockBean CustomUserDetailsService details; @MockBean JwtService jwt;
 @Test void forgotAndResendArePublicAndUseSocketAddressNotUntrustedForwardedHeaders()throws Exception{
  when(resets.sendOtp(anyString(),eq("192.0.2.1"))).thenReturn(60);
  for(String path:java.util.List.of("/forgot-password","/forgot-password/resend")){
   mvc.perform(post("/api/auth"+path).with(request->{request.setRemoteAddr("192.0.2.1");return request;})
    .header("X-Forwarded-For","198.51.100.99").contentType("application/json").content("{\"email\":\"test@example.test\"}"))
    .andExpect(status().isOk()).andExpect(header().string("Retry-After","60"))
    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("If this account"))).andExpect(jsonPath("$.retryAfterSeconds").value(60));
  }
  verify(resets,times(2)).sendOtp("test@example.test","192.0.2.1");verifyNoInteractions(jwt,details);
 }
 @Test void knownAndUnknownAccountsHaveIdenticalRequestResponses()throws Exception{
  when(resets.sendOtp(anyString(),anyString())).thenReturn(60);
  String known=mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{\"email\":\"known@example.test\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{\"email\":\"missing@example.test\"}")).andExpect(status().isOk()).andExpect(content().string(known));
 }
 @Test void resetUsesRemoteIpAndRemainsPublic()throws Exception{
  mvc.perform(post("/api/auth/reset-password").contentType("application/json").content("{\"email\":\"test@example.test\",\"otp\":\"123456\",\"newPassword\":\"Replacement-123\"}")).andExpect(status().isOk());
  verify(resets).resetPassword(eq("test@example.test"),eq("123456"),eq("Replacement-123"),anyString());verifyNoInteractions(jwt,details);
 }
 @Test void invalidExpiredUsedOrLockedCodeHasSameSafeError()throws Exception{
  doThrow(new PasswordResetException(400)).when(resets).resetPassword(anyString(),anyString(),anyString(),anyString());
  mvc.perform(post("/api/auth/reset-password").contentType("application/json").content("{\"email\":\"test@example.test\",\"otp\":\"123456\",\"newPassword\":\"Replacement-123\"}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESET_REQUEST"))
   .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("123456"))));
 }
 @Test void rateLimitIsActionableWithoutSecrets()throws Exception{
  when(resets.sendOtp(anyString(),anyString())).thenThrow(new PasswordResetException(429,60));
  doThrow(new PasswordResetException(429,60)).when(resets).resetPassword(anyString(),anyString(),anyString(),anyString());
  for(String path:java.util.List.of("/forgot-password","/forgot-password/resend","/reset-password"))
   mvc.perform(post("/api/auth"+path).contentType("application/json").content("{\"email\":\"test@example.test\",\"otp\":\"123456\",\"newPassword\":\"Replacement-123\"}"))
    .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"))
    .andExpect(jsonPath("$.code").value("PASSWORD_RESET_RATE_LIMITED")).andExpect(jsonPath("$.retryAfterSeconds").value(60))
    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Replacement-123"))));
 }
 @Test void malformedInputsDoNotReachService()throws Exception{
  for(String path:java.util.List.of("/forgot-password","/forgot-password/resend","/reset-password"))for(String body:java.util.List.of("{}","{\"email\":\"invalid\"}","{"))
   mvc.perform(post("/api/auth"+path).contentType("application/json").content(body)).andExpect(status().isBadRequest());
  verifyNoInteractions(resets,users,jwt,details);
 }
 @Test void storageFailureFailsClosedWithoutInternalDetails()throws Exception{
  when(resets.sendOtp(anyString(),anyString())).thenThrow(new IllegalStateException("private database detail"));
  mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{\"email\":\"test@example.test\"}"))
   .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("PASSWORD_RESET_FAILED"))
   .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private database detail"))));
 }
 @Test void protectedRoutesStillRequireJwt()throws Exception{
  for(String path:java.util.List.of("/api/admin/dashboard","/api/student/dashboard","/api/professor/dashboard","/api/super-admin/dashboard"))mvc.perform(get(path)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/change-password").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());verifyNoInteractions(resets,users);
 }
}