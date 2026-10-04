package com.ptc.halo;

import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.StudentAssessmentController;
import com.ptc.halo.dtoResponse.AssessmentResponse;
import com.ptc.halo.entity.AssessmentAttemptEntity;
import com.ptc.halo.entity.AssessmentEntity;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import java.util.Optional;
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

@WebMvcTest(StudentAssessmentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class StudentAssessmentGenerationAccessTest {
    @Autowired MockMvc mvc;
    @MockBean AssessmentService assessments;
    @MockBean UserRepository users;
    @MockBean CustomUserDetailsService details;
    @MockBean JwtService jwt;

    void token(String role) {
        when(jwt.isCurrent(eq("test-token"), any())).thenReturn(true);
        when(jwt.extractUsername("test-token")).thenReturn("student@example.test");
        when(details.loadUserByUsername("student@example.test")).thenReturn(
                User.withUsername("student@example.test").password("unused").roles(role).build());
    }

    @Test void studentCannotGenerateForAnyModule() throws Exception {
        token("STUDENT");
        // The removed route cannot inspect or generate any module, eligible or otherwise.
        for (long moduleId : new long[]{1L, 2L, 99999L}) {
            mvc.perform(post("/api/student/assessment/generate/" + moduleId)
                    .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isNotFound());
        }
        verifyNoInteractions(assessments, users);
    }

    @Test void unauthenticatedRequestsStillRequireAuthentication() throws Exception {
        mvc.perform(post("/api/student/assessment/generate/1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/student/assessment/1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(assessments);
    }

    @Test void professorCannotUseStudentRoute() throws Exception {
        token("PROFESSOR");
        mvc.perform(post("/api/student/assessment/generate/1")
                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(assessments);
    }

    @Test void studentCanReadAndStartExistingAssessment() throws Exception {
        token("STUDENT");
        var student = new UserEntity();
        when(users.findByEmail("student@example.test")).thenReturn(Optional.of(student));
        var response = new AssessmentResponse();
        response.setTitle("Existing assessment");
        when(assessments.getAssessment(12L, student)).thenReturn(response);
        var assessment = mock(AssessmentEntity.class);
        when(assessment.getId()).thenReturn(34L);
        var attempt = mock(AssessmentAttemptEntity.class);
        when(attempt.getId()).thenReturn(56L);
        when(attempt.getAssessment()).thenReturn(assessment);
        when(assessments.startAttempt(12L, student)).thenReturn(attempt);
        mvc.perform(get("/api/student/assessment/12").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Existing assessment"));
        mvc.perform(post("/api/student/assessment/start/12").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attemptId").value(56))
                .andExpect(jsonPath("$.assessmentId").value(34));
        verify(assessments).getAssessment(12L, student);
        verify(assessments).startAttempt(12L, student);
        verifyNoMoreInteractions(assessments);
    }
}
