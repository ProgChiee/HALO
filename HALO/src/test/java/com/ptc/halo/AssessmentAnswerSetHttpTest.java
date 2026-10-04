package com.ptc.halo;

import com.ptc.halo.controller.StudentAssessmentController;
import com.ptc.halo.controller.StudentLessonExceptionHandler;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.service.AssessmentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssessmentAnswerSetHttpTest {
 @Test void validationAndDatabaseConflictAreSafeStructuredClientErrors() throws Exception {
  var service=mock(AssessmentService.class);var users=mock(UserRepository.class);var student=new UserEntity();student.setId(1L);
  when(users.findByEmail("student@example.test")).thenReturn(Optional.of(student));
  var mvc=MockMvcBuilders.standaloneSetup(new StudentAssessmentController(service,users)).setControllerAdvice(new StudentLessonExceptionHandler()).build();
  for(var code:new String[]{"INVALID_ANSWER_SET","ANSWERS_ALREADY_RECORDED"}) {
   int status=code.equals("INVALID_ANSWER_SET")?400:409;
   doThrow(new ResponseStatusException(HttpStatus.valueOf(status),code)).when(service).submitAttempt(eq(2L),eq(student),any());
   mvc.perform(post("/api/student/assessment/submit/2")
     .principal(new UsernamePasswordAuthenticationToken("student@example.test","unused"))
     .contentType("application/json").content("{\"answers\":[{\"questionId\":1,\"answer\":\"A\"},{\"questionId\":1,\"answer\":\"A\"}]}"))
     .andExpect(status().is(status)).andExpect(jsonPath("$.status").value(status))
     .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.message").value(code))
     .andExpect(jsonPath("$.trace").doesNotExist());
  }
 }
}
