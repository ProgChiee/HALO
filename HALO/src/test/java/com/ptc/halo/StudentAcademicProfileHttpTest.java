package com.ptc.halo;

import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.StudentProfileController;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StudentProfileController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,StudentAcademicProfileService.class,ProfileService.class})
class StudentAcademicProfileHttpTest {
    @Autowired MockMvc mvc;
    @MockBean UserRepository users;
    @MockBean StudentProfileRepository profiles;
    @MockBean ProfessorRepository professors;
    @MockBean ActivityLogService activity;
    @MockBean CustomUserDetailsService details;
    @MockBean JwtService jwt;
    UserEntity student;StudentProfileEntity profile;
    @BeforeEach void setup() {
        student=new UserEntity();student.setId(5L);student.setName("Owner");student.setEmail("owner@example.test");student.setRole(Role.STUDENT);student.setStatus(Status.ACTIVE);
        profile=new StudentProfileEntity();profile.setUser(student);profile.setStudentId("29AHRT-0001");profile.setSection("BSHM 1A");profile.setYearLevel(YearLevel.FIRST_YEAR);
        when(jwt.extractUserId("token")).thenReturn(5L);when(jwt.isCurrent(eq("token"),any())).thenReturn(true);
        role("STUDENT");when(users.findForAssessmentLifecycle(5L)).thenReturn(Optional.of(student));when(users.findById(5L)).thenReturn(Optional.of(student));when(profiles.findByUserId(5L)).thenReturn(Optional.of(profile));
    }
    void role(String role) {when(details.loadUserById(5L)).thenReturn(TestIdentity.details(5L,"old-reused-email@example.test",role));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String body) {
        return patch("/api/student/profile/academic").header("Authorization","Bearer token").contentType("application/json").content(body);
    }
    @Test void patchUsesImmutableOwnerAndRefreshReturnsPersistedFields() throws Exception {
        mvc.perform(request("{\"field\":\"SECTION\",\"value\":\" BSHM  2A \"}").param("userId","999"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(5)).andExpect(jsonPath("$.section").value("BSHM 2A"));
        mvc.perform(get("/api/student/profile").header("Authorization","Bearer token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.section").value("BSHM 2A"));
        verify(users,never()).findById(999L);verify(users,never()).findByEmail(anyString());
        verify(profiles,never()).findByUserId(999L);
    }
    @Test void badFieldsMassAssignmentAndActingHeadersAreRejected() throws Exception {
        for(String extra:List.of("userId","email","role","status","password"))
            mvc.perform(request("{\"field\":\"SECTION\",\"value\":\"BSHM 2A\",\""+extra+"\":\"999\"}")).andExpect(status().isBadRequest());
        for(String body:List.of("{}","{\"field\":\"ROLE\",\"value\":\"ADMIN\"}","{\"field\":\"STUDENT_ID\",\"value\":\"INVALID\"}","{\"field\":\"SECTION\",\"value\":\" \"}","{\"field\":\"YEAR_LEVEL\",\"value\":\"THIRD_YEAR\"}"))
            mvc.perform(request(body)).andExpect(status().isBadRequest());
        mvc.perform(request("{\"field\":\"SECTION\",\"value\":\"BSHM 2A\"}").header("X-Acting-Session","session"))
            .andExpect(status().isForbidden());
        verify(profiles,never()).saveAndFlush(any());
    }
    @Test void roleRestrictionsAndPreviewSupportCannotBypassEndpoint() throws Exception {
        mvc.perform(patch("/api/student/profile/academic").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        for(String role:List.of("ADMIN","SUPER_ADMIN","PROFESSOR")) {
            role(role);mvc.perform(request("{\"field\":\"SECTION\",\"value\":\"BSHM 2A\"}")).andExpect(status().isForbidden());
        }
        verify(profiles,never()).saveAndFlush(any());
    }
    @Test void duplicateConstraintFailureReturnsSafeConflict() throws Exception {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("private database details")).when(profiles).saveAndFlush(any());
        mvc.perform(request("{\"field\":\"STUDENT_ID\",\"value\":\"29AHRT-0002\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STUDENT_ID_CONFLICT"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private database details"))));
    }
}
