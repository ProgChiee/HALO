package com.ptc.halo;

import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.*;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({StudentProfileController.class,StudentLearningProgressionController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,ProfileService.class,StudentSubjectService.class})
class StudentIncompleteProfileHttpTest {
    @Autowired MockMvc mvc;
    @MockBean UserRepository users;
    @MockBean StudentProfileRepository profiles;
    @MockBean StudentAcademicProfileService academicProfiles;
    @MockBean ProfessorRepository professors;
    @MockBean SubjectRepository subjects;
    @MockBean WeekRepository weeks;
    @MockBean AiLearningModuleRepository modules;
    @MockBean StudentModuleProgressRepository progress;
    @MockBean StudentLearningProgressionService progression;
    @MockBean CustomUserDetailsService details;
    @MockBean JwtService jwt;
    @BeforeEach void setup() {
        when(jwt.extractUserId("test-token")).thenReturn(26L);
        when(jwt.isCurrent(eq("test-token"),any())).thenReturn(true); role("STUDENT");
        var user=new UserEntity();user.setId(26L);user.setName("Own name");user.setEmail("student@example.test");
        user.setRole(Role.STUDENT);user.setStatus(Status.ACTIVE);
        when(users.findById(26L)).thenReturn(Optional.of(user));
    }
    void role(String role) {
        when(details.loadUserById(26L)).thenReturn(
                TestIdentity.details(26L, "student@example.test", role));
    }
    @Test void selfProfileIgnoresOtherUserQueryParametersAndReturnsSafeIncompleteState() throws Exception {
        mvc.perform(get("/api/student/profile?userId=999&studentId=999").header("Authorization","Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(26))
                .andExpect(jsonPath("$.name").value("Own name")).andExpect(jsonPath("$.profileIncomplete").value(true))
                .andExpect(jsonPath("$.studentId").isEmpty()).andExpect(jsonPath("$.yearLevel").isEmpty());
        verify(profiles).findByUserId(26L);verify(profiles,never()).findByUserId(999L);
    }
    @Test void subjectsReturnRecoveryResponseWithoutBroadeningScope() throws Exception {
        mvc.perform(get("/api/student/subjects?yearLevel=FIRST_YEAR").header("Authorization","Bearer test-token"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROFILE_INCOMPLETE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Contact an administrator")));
        verifyNoInteractions(subjects,weeks);
    }
    @Test void unauthenticatedAndOtherRolesCannotReadStudentProfileOrSubjects() throws Exception {
        for (String path:new String[]{"/api/student/profile","/api/student/subjects"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for (String role:new String[]{"ADMIN","SUPER_ADMIN","PROFESSOR"}) {
                role(role);mvc.perform(get(path).header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(profiles,subjects);
    }
    @Test void legacyWholeProfilePutRemainsUnavailable() throws Exception {
        mvc.perform(put("/api/student/profile").header("Authorization","Bearer test-token")
                .contentType("application/json").content("{\"yearLevel\":\"SECOND_YEAR\"}"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(profiles);
    }
}
