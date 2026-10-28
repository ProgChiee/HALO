package com.ptc.halo;

import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.ProfessorProfileController;
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

@WebMvcTest(ProfessorProfileController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,ProfessorIdProfileService.class,ProfileService.class})
class ProfessorIdProfileHttpTest {
    @Autowired MockMvc mvc;
    @MockBean UserRepository users;
    @MockBean StudentProfileRepository profiles;
    @MockBean ProfessorRepository professors;
    @MockBean ActivityLogService activity;
    @MockBean CustomUserDetailsService details;
    @MockBean JwtService jwt;
    UserEntity student;ProfessorEntity profile;
    @BeforeEach void setup() {
        student=new UserEntity();student.setId(5L);student.setName("Owner");student.setEmail("owner@example.test");student.setRole(Role.PROFESSOR);student.setStatus(Status.ACTIVE);
        profile=new ProfessorEntity();profile.setUser(student);profile.setProfessorId("PROF-001");
        when(jwt.extractUserId("token")).thenReturn(5L);when(jwt.isCurrent(eq("token"),any())).thenReturn(true);
        role("PROFESSOR");when(users.findForAssessmentLifecycle(5L)).thenReturn(Optional.of(student));when(users.findById(5L)).thenReturn(Optional.of(student));when(professors.findByUserId(5L)).thenReturn(Optional.of(profile));
    }
    void role(String role) {when(details.loadUserById(5L)).thenReturn(TestIdentity.details(5L,"old-reused-email@example.test",role));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String body) {
        return patch("/api/professor/profile/id").header("Authorization","Bearer token").contentType("application/json").content(body);
    }
    @Test void patchUsesImmutableOwnerAndRefreshReturnsPersistedFields() throws Exception {
        mvc.perform(request("{\"professorId\":\" NEW-123 \"}").param("userId","999"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(5)).andExpect(jsonPath("$.professorId").value("NEW-123"));
        mvc.perform(get("/api/professor/profile").header("Authorization","Bearer token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.professorId").value("NEW-123"));
        verify(users,never()).findById(999L);verify(users,never()).findByEmail(anyString());
        verify(professors,never()).findByUserId(999L);
    }
    @Test void badFieldsMassAssignmentAndActingHeadersAreRejected() throws Exception {
        for(String extra:List.of("userId","email","role","status","password"))
            mvc.perform(request("{\"professorId\":\"NEW-123\",\""+extra+"\":\"999\"}")).andExpect(status().isBadRequest());
        for(String id:List.of("", "   ", "A".repeat(256)))
            mvc.perform(request("{\"professorId\":\""+id+"\"}")).andExpect(status().isBadRequest());
        mvc.perform(request("{\"professorId\":\"NEW-123\"}").header("X-Acting-Session","session")).andExpect(status().isForbidden());
        verify(professors,never()).saveAndFlush(any());
    }
    @Test void roleRestrictionsAndPreviewSupportCannotBypassEndpoint() throws Exception {
        mvc.perform(patch("/api/professor/profile/id").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        for(String role:List.of("ADMIN","SUPER_ADMIN","STUDENT")) {
            role(role);mvc.perform(request("{\"professorId\":\"NEW-123\"}")).andExpect(status().isForbidden());
        }
        verify(professors,never()).saveAndFlush(any());
    }
    @Test void duplicateConstraintFailureReturnsSafeConflict() throws Exception {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("private database details")).when(professors).saveAndFlush(any());
        mvc.perform(request("{\"professorId\":\"NEW-123\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROFESSOR_ID_CONFLICT"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private database details"))));
    }
}
