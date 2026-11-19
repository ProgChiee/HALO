package com.ptc.halo;
import com.ptc.halo.controller.*;
import com.ptc.halo.service.*;
import com.ptc.halo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class RegistrationHttpValidationTest {
 @Test void bothRegistrationEndpointsPreserveRetryAfterAndNeverReachAccountCreation()throws Exception {
  var auth=mock(AuthService.class);var registry=mock(StudentRegistryService.class);
  var controller=new AuthController(auth,mock(PasswordResetService.class),mock(UserRepository.class));
  org.springframework.test.util.ReflectionTestUtils.setField(controller,"registry",registry);
  doThrow(new RegistrationRateLimitException(2797)).when(registry).requestCode(anyString(),anyString(),anyString());
  doThrow(new RegistrationRateLimitException(2797)).when(registry).admit(anyString(),anyString(),eq(true));
  var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new PasswordChangeValidationHandler()).build();
  for(String endpoint:java.util.List.of("/register/student/code","/register/student"))
   mvc.perform(post("/api/auth"+endpoint).contentType("application/json").content("{\"studentId\":\"29AHRT-9001\",\"email\":\"synthetic@paterostechnologicalcollege.edu.ph\"}"))
    .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","2797"))
    .andExpect(jsonPath("$.code").value("REGISTRATION_RATE_LIMITED"))
    .andExpect(jsonPath("$.retryAfterSeconds").value(2797))
    .andExpect(jsonPath("$.message").value("Too many registration attempts. Please wait before trying again."));
  verifyNoInteractions(auth);verify(registry,never()).claim(anyLong(),anyLong());
 }
 @Test void invalidYearReturnsSafeFieldError() throws Exception {
  var service=mock(AuthService.class);
  var controller=new AuthController(service,mock(PasswordResetService.class),mock(UserRepository.class));
  org.springframework.test.util.ReflectionTestUtils.setField(controller,"registry",mock(StudentRegistryService.class));
  var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new PasswordChangeValidationHandler()).build();
  mvc.perform(post("/api/auth/register/student").contentType("application/json").content("{\"yearLevel\":\"THIRD_YEAR\",\"password\":\"never-echo-this\"}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.yearLevel").exists()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("never-echo-this"))));
  verifyNoInteractions(service);
 }
 @Test void duplicateErrorsRemainStructuredAndSafe() throws Exception {
  var service=mock(AuthService.class);when(service.registerStudent(any())).thenThrow(new RegistrationValidationException(java.util.Map.of("studentId","This Student ID is already registered."),409));
  var controller=new AuthController(service,mock(PasswordResetService.class),mock(UserRepository.class));
  org.springframework.test.util.ReflectionTestUtils.setField(controller,"registry",mock(StudentRegistryService.class));
  var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new PasswordChangeValidationHandler()).build();
  mvc.perform(post("/api/auth/register/student").contentType("application/json").content("{}"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGISTRATION_VALIDATION_FAILED")).andExpect(jsonPath("$.errors.studentId").exists());
 }
}
