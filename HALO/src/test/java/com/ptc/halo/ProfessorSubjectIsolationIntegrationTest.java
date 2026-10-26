package com.ptc.halo;

import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.ProfessorAcademicController;
import com.ptc.halo.repository.*;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Tests 7E/7F: real security/filter/controller/ownership query against synthetic H2 rows. */
@SpringBootTest(classes=ProfessorSubjectIsolationIntegrationTest.Config.class, properties={
    "spring.datasource.url=jdbc:h2:mem:professor7e;MODE=MariaDB;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.open-in-view=false",
    "jwt.secret=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=",
    "spring.ai.google.genai.chat.enabled=false", "spring.ai.model.chat=none"
})
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("professor-7e-test")
@Transactional
class ProfessorSubjectIsolationIntegrationTest {
    @Configuration
    @Profile("professor-7e-test")
    @EnableAutoConfiguration
    @EntityScan("com.ptc.halo.entity")
    @EnableJpaRepositories("com.ptc.halo.repository")
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
        CustomUserDetailsService.class, ProfessorAcademicController.class, ProfessorAcademicService.class,
        com.ptc.halo.controller.StudentDashboardController.class,
        com.ptc.halo.controller.AdminController.class, com.ptc.halo.controller.SuperAdminController.class})
    static class Config {}

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired UserRepository users;
    @Autowired SubjectRepository subjects;
    @Autowired JwtService jwt;
    @MockBean ActivityLogService audit;
    @MockBean StudentDashboardService studentDashboard;
    @MockBean AdminService admin;
    @MockBean SuperAdminService superAdmin;
    @MockBean ProfileService profiles;
    private String tokenA, tokenB;

    @BeforeEach void fixtures() {
        db.update("insert into user_entity(id,name,email,password,role,status,token_version,must_change_password) values(48,'Synthetic Professor A','a@example.invalid','unused','PROFESSOR','ACTIVE',0,false),(35,'Synthetic Professor B','b@example.invalid','unused','PROFESSOR','ACTIVE',0,false)");
        db.update("insert into professor_entity(id,user_id,professor_id) values(9,48,'PROF-011'),(7,35,'PROF-TEST-B')");
        db.update("insert into subjects(id,professor_id,subject_code,subject_name,year_level) values(15,9,'HRM-2222','Synthetic owned subject','FIRST_YEAR'),(10,7,'FOREIGN-TEST','Synthetic foreign subject','FIRST_YEAR')");
        assertEquals(48L,subjects.findById(15L).orElseThrow().getProfessor().getUser().getId());
        assertEquals(35L,subjects.findById(10L).orElseThrow().getProfessor().getUser().getId());
        tokenA=jwt.generateToken(users.findById(48L).orElseThrow());
        tokenB=jwt.generateToken(users.findById(35L).orElseThrow());
        assertEquals(48L,jwt.extractUserId(tokenA));assertEquals(35L,jwt.extractUserId(tokenB));
    }
    @Test void scenarioAOwnSubjectReturns200() throws Exception {
        mvc.perform(get("/api/professor/subjects/15").header("Authorization","Bearer "+tokenA))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(15)).andExpect(jsonPath("$.subjectCode").value("HRM-2222"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={
        "/api/student/dashboard", "/api/admin/students/48", "/api/super-admin/admin/48"
    })
    void test7FCrossRoleEndpointsRejectProfessorAndAnonymous(String endpoint) throws Exception {
        // The same signed Professor JWT is accepted on its authorized route.
        mvc.perform(get("/api/professor/subjects/15").header("Authorization","Bearer "+tokenA))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(15));
        mvc.perform(get(endpoint).header("Authorization","Bearer "+tokenA))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_ROLE_OR_ACCESS"));
        mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        // Neither resource IDs nor client-provided identity/role parameters grant access.
        mvc.perform(get(endpoint.replace("/48", "/35"))
                .param("userId","35").param("role","SUPER_ADMIN").param("professorId","7")
                .header("X-Request-ID","00000000-0000-0000-0000-000000000035")
                .header("Authorization","Bearer "+tokenA))
            .andExpect(status().isForbidden());
        mvc.perform(get(endpoint.replace("/48", "/35")).param("role","SUPER_ADMIN"))
            .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(studentDashboard,admin,superAdmin,profiles,audit);
    }
    @Test void scenarioBForeignSubjectReturns404WithoutData() throws Exception { denied(tokenA,10); }
    @Test void scenarioCAnonymousReturns401() throws Exception {
        mvc.perform(get("/api/professor/subjects/15")).andExpect(status().isUnauthorized());
    }
    @Test void scenarioDCrossProfessorIsolationIsSymmetric() throws Exception {
        mvc.perform(get("/api/professor/subjects/10").header("Authorization","Bearer "+tokenB))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(10)).andExpect(jsonPath("$.subjectCode").value("FOREIGN-TEST"));
        denied(tokenB,15);
    }
    private void denied(String token,int id) throws Exception {
        var response=mvc.perform(get("/api/professor/subjects/"+id).header("Authorization","Bearer "+token))
            .andExpect(status().isNotFound()).andReturn().getResponse();
        assertFalse(response.getContentAsString().contains("Synthetic"));
        assertFalse(response.getContentAsString().contains("HRM-2222"));
        assertFalse(response.getContentAsString().contains("FOREIGN-TEST"));
    }
}
