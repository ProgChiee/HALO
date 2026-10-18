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
 @Test void invalidYearReturnsSafeFieldError() throws Exception {
  var service=mock(AuthService.class);
  var mvc=MockMvcBuilders.standaloneSetup(new AuthController(service,mock(PasswordResetService.class),mock(UserRepository.class))).setControllerAdvice(new PasswordChangeValidationHandler()).build();
  mvc.perform(post("/api/auth/register/student").contentType("application/json").content("{\"yearLevel\":\"THIRD_YEAR\",\"password\":\"never-echo-this\"}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.yearLevel").exists()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("never-echo-this"))));
  verifyNoInteractions(service);
 }
 @Test void duplicateErrorsRemainStructuredAndSafe() throws Exception {
  var service=mock(AuthService.class);when(service.registerStudent(any())).thenThrow(new RegistrationValidationException(java.util.Map.of("studentId","This Student ID is already registered."),409));
  var mvc=MockMvcBuilders.standaloneSetup(new AuthController(service,mock(PasswordResetService.class),mock(UserRepository.class))).setControllerAdvice(new PasswordChangeValidationHandler()).build();
  mvc.perform(post("/api/auth/register/student").contentType("application/json").content("{}"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGISTRATION_VALIDATION_FAILED")).andExpect(jsonPath("$.errors.studentId").exists());
 }
}
