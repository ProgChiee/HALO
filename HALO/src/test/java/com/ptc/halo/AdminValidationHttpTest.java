package com.ptc.halo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.AdminController;
import com.ptc.halo.dtoRequest.*;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminValidationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean AdminService admins;
    @MockBean UserRepository users;
    @MockBean ActivityLogService logs;
    @MockBean ProfileService profiles;
    @MockBean CustomUserDetailsService details;
    @MockBean JwtService jwt;
    UserEntity actor;

    @BeforeEach void setup() {
        actor = new UserEntity(); actor.setEmail("admin@example.test"); actor.setRole(Role.ADMIN);
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(jwt.extractUsername("test-token")).thenReturn(actor.getEmail());
        when(jwt.isCurrent(eq("test-token"), any())).thenReturn(true);
        role("ADMIN");
    }
    void role(String role) {
        when(details.loadUserByUsername(actor.getEmail())).thenReturn(
                User.withUsername(actor.getEmail()).password("unused").roles(role).build());
    }
    Map<String, Object> professor() {
        return new HashMap<>(Map.of("name", "Professor", "email", "prof@example.test",
                "password", "password-123", "professorId", "P-1"));
    }
    Map<String, Object> student() {
        return new HashMap<>(Map.of("name", "Student", "studentId", "S-1", "section", "A", "yearLevel", "FIRST_YEAR"));
    }
    void invalidProfessor(String field, Object value, boolean update) throws Exception {
        var body = professor(); body.put(field, value);
        mvc.perform((update ? put("/api/admin/professors/1") : post("/api/admin/create-professor"))
                .header("Authorization", "Bearer test-token").contentType("application/json").content(json.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors." + field).exists());
    }
    @Test void createRejectsMissingBlankMalformedAndOversizedFields() throws Exception {
        for (String field : List.of("name", "email", "password", "professorId")) {
            invalidProfessor(field, null, false);
            invalidProfessor(field, "", false);
            invalidProfessor(field, "   ", false);
        }
        invalidProfessor("email", "invalid-email", false);
        invalidProfessor("password", "short", false);
        invalidProfessor("password", "a".repeat(73), false);
        invalidProfessor("name", "a".repeat(101), false);
        invalidProfessor("email", "a".repeat(245) + "@example.test", false);
        invalidProfessor("professorId", "a".repeat(256), false);
        var body = professor(); body.put("password", "é".repeat(37));
        mvc.perform(post("/api/admin/create-professor").header("Authorization", "Bearer test-token")
                .contentType("application/json").content(json.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.passwordWithinByteLimit").exists());
        verifyNoInteractions(admins);
    }
    @Test void updateRejectsInvalidRequiredValues() throws Exception {
        for (String field : List.of("name", "email", "professorId")) {
            invalidProfessor(field, null, true);
            invalidProfessor(field, "   ", true);
        }
        invalidProfessor("email", "invalid", true);
        verifyNoInteractions(admins);
    }
    @Test void studentRejectsMissingRequiredFieldsAndInvalidEnum() throws Exception {
        for (String field : List.of("name", "studentId", "section", "yearLevel")) {
            var body = student(); body.put(field, null);
            mvc.perform(put("/api/admin/students/1").header("Authorization", "Bearer test-token")
                    .contentType("application/json").content(json.writeValueAsBytes(body)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors." + field).exists());
        }
        for (String field : List.of("name", "studentId", "section")) {
            var body = student(); body.put(field, "  ");
            mvc.perform(put("/api/admin/students/1").header("Authorization", "Bearer test-token")
                    .contentType("application/json").content(json.writeValueAsBytes(body)))
                    .andExpect(status().isBadRequest());
        }
        var body = student(); body.put("yearLevel", "PRIVATE_INVALID_ENUM");
        mvc.perform(put("/api/admin/students/1").header("Authorization", "Bearer test-token")
                .contentType("application/json").content(json.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE_INVALID_ENUM"))));
        verifyNoInteractions(admins);
    }
    @Test void validBodiesAreNormalizedBeforeReachingServices() throws Exception {
        var body = professor(); body.put("name", "  Professor  "); body.put("email", " PROF@Example.test ");
        body.put("professorId", " P-1 "); body.put("password", "é".repeat(36));
        mvc.perform(post("/api/admin/create-professor").header("Authorization", "Bearer test-token")
                .contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().isOk());
        verify(admins).createProfessor(argThat(r -> r.getName().equals("Professor") && r.getEmail().equals("prof@example.test")
                && r.getProfessorId().equals("P-1") && r.getPassword().equals("é".repeat(36))), eq(actor));
        body.remove("password");
        mvc.perform(put("/api/admin/professors/1").header("Authorization", "Bearer test-token")
                .contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().isOk());
        verify(admins).updateProfessor(eq(1L), argThat(r -> r.getName().equals("Professor") && r.getEmail().equals("prof@example.test")
                && r.getProfessorId().equals("P-1")), eq(actor));
        var student = student(); student.put("name", " Student "); student.put("studentId", " S-1 "); student.put("section", " A ");
        mvc.perform(put("/api/admin/students/1").header("Authorization", "Bearer test-token")
                .contentType("application/json").content(json.writeValueAsBytes(student))).andExpect(status().isOk());
        verify(admins).updateStudent(eq(1L), argThat(r -> r.getName().equals("Student") && r.getStudentId().equals("S-1")
                && r.getSection().equals("A") && r.getYearLevel() == YearLevel.FIRST_YEAR), eq(actor));
    }
    @Test void idsAreValidatedOnAllSixEndpoints() throws Exception {
        for (String group : List.of("professors", "students")) {
            for (String id : List.of("0", "-1", "invalid")) {
                String path = "/api/admin/" + group + "/" + id;
                mvc.perform(get(path).header("Authorization", "Bearer test-token")).andExpect(status().isBadRequest());
                mvc.perform(patch(path + "/status").header("Authorization", "Bearer test-token")).andExpect(status().isBadRequest());
                mvc.perform(put(path).header("Authorization", "Bearer test-token").contentType("application/json")
                        .content(json.writeValueAsBytes(group.equals("professors") ? professor() : student())))
                        .andExpect(status().isBadRequest());
            }
        }
        verifyNoInteractions(admins);
    }
    @Test void authorizationRemainsRequired() throws Exception {
        mvc.perform(post("/api/admin/create-professor").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        for (String role : List.of("STUDENT", "PROFESSOR", "SUPER_ADMIN")) {
            role(role);
            mvc.perform(post("/api/admin/create-professor").header("Authorization", "Bearer test-token")
                    .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(admins);
    }
    @Test void businessErrorsAreStructuredAndSafe() throws Exception {
        for (var code : AdminApiException.Code.values()) {
            doThrow(new AdminApiException(code)).when(admins).viewProfessorById(1L);
            mvc.perform(get("/api/admin/professors/1").header("Authorization", "Bearer test-token"))
                .andExpect(status().is(code.status)).andExpect(jsonPath("$.status").value(code.status))
                .andExpect(jsonPath("$.code").value(code.name())).andExpect(jsonPath("$.message").value(code.message));
        }
        doThrow(new AdminApiException(AdminApiException.Code.RESOURCE_NOT_FOUND)).when(admins).viewStudentById(1L);
        mvc.perform(get("/api/admin/students/1").header("Authorization", "Bearer test-token"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        for (RuntimeException error : List.of(new IllegalStateException("SECRET SQL path token"),
                new org.springframework.dao.DataIntegrityViolationException("SECRET unrelated constraint"))) {
            doThrow(error).when(admins).viewProfessorById(1L);
            mvc.perform(get("/api/admin/professors/1").header("Authorization", "Bearer test-token"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("ADMIN_REQUEST_FAILED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET"))));
        }
        doThrow(new org.springframework.dao.DataIntegrityViolationException("SECRET",
            new java.sql.SQLException("duplicate key uk_user_email_normalized SECRET"))).when(admins).updateProfessor(eq(1L), any(), any());
        mvc.perform(put("/api/admin/professors/1").header("Authorization", "Bearer test-token")
                .contentType("application/json").content(json.writeValueAsBytes(professor())))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET"))));
    }
}
