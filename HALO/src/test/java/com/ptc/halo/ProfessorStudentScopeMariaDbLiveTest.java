package com.ptc.halo;

import com.ptc.halo.enums.Role;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.service.ProfessorStudentService;
import com.ptc.halo.service.StudentSubjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, server-enforced read-only verification; never creates fixtures or updates the schema. */
@EnabledIfSystemProperty(named="halo.test.professor.scope.live",matches="true")
@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=none","spring.sql.init.mode=never",
    "spring.datasource.hikari.read-only=true",
    "spring.datasource.hikari.connection-init-sql=SET SESSION TRANSACTION READ ONLY",
    "spring.jpa.open-in-view=false"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({ProfessorStudentService.class,StudentSubjectService.class})
@Transactional(readOnly=true)
class ProfessorStudentScopeMariaDbLiveTest {
    @Autowired ProfessorStudentService service;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test void legacyListAndEveryDetailEnforceRealAcademicScope() {
        assertTrue(jdbc.queryForObject("select version()",String.class).contains("MariaDB"));
        assertEquals(1,jdbc.queryForObject("select @@tx_read_only",Integer.class));
        var professors=users.findByRole(Role.PROFESSOR);
        assertFalse(professors.isEmpty());
        var students=users.findByRole(Role.STUDENT);
        for(var professor:professors) {
            var eligible=jdbc.queryForList("""
                select distinct u.id from user_entity u
                join student_profile_entity profile on profile.user_id=u.id
                join subjects s on s.year_level=profile.year_level
                join professor_entity p on p.id=s.professor_id
                where u.role='STUDENT' and p.user_id=? order by u.id
                """,Long.class,professor.getId());
            assertEquals(eligible,service.getStudents(professor).stream().map(r->r.getUserId()).toList());
            for(var student:students) {
                var reads=java.util.List.<Runnable>of(
                    ()->service.getStudentProgress(student.getId(),professor),
                    ()->service.getStudentSubjects(student.getId(),professor),
                    ()->service.getStudentAssessmentHistory(student.getId(),professor),
                    ()->service.getStudentBadges(student.getId(),professor));
                for(var read:reads) {
                    if(eligible.contains(student.getId()))assertDoesNotThrow(read::run);
                    else assertEquals(404,assertThrows(ResponseStatusException.class,read::run).getStatusCode().value());
                }
            }
            System.out.println("PROFESSOR_SCOPE_READ_ONLY professorId="+professor.getId()+" eligibleStudentsAllStates="+eligible.size());
        }
    }
}
