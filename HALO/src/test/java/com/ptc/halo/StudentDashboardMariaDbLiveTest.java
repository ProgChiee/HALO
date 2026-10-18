package com.ptc.halo;

import com.ptc.halo.service.ProfessorDashboardService;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.enums.Role;
import com.ptc.halo.enums.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in verification only. Existing MariaDB records, server-enforced read-only connection, no schema changes. */
@EnabledIfSystemProperty(named="halo.test.dashboard.live",matches="true")
@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=none","spring.sql.init.mode=never",
 "spring.datasource.hikari.read-only=true","spring.datasource.hikari.connection-init-sql=SET SESSION TRANSACTION READ ONLY",
 "spring.jpa.show-sql=false","logging.level.org.hibernate.SQL=OFF"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({com.ptc.halo.service.StudentDashboardService.class,com.ptc.halo.service.StudentStudyActivityService.class})
@Transactional(readOnly=true)
class StudentDashboardMariaDbLiveTest {
 @Autowired com.ptc.halo.service.StudentDashboardService service;
 @Autowired UserRepository users; @Autowired JdbcTemplate jdbc;
 @Test void liveMetricsMatchReadOnlyRecords(){
  assertEquals(1,jdbc.queryForObject("select @@tx_read_only",Integer.class));
  for(var u:users.findByRole(Role.STUDENT)){
   var d=service.getDashboard(u);
   assertEquals(jdbc.queryForObject("select count(*) from student_module_progress where student_id=? and completed=true",Long.class,u.getId()),d.getCompletedModules());
   assertEquals(jdbc.queryForObject("select count(*) from assessment_attempts where student_id=? and passed=true",Long.class,u.getId()),d.getPassedAssessments());
   assertEquals(jdbc.queryForObject("select count(*) from student_badges where student_id=?",Long.class,u.getId()),d.getTotalBadges());
   var scores=jdbc.queryForList("select score from assessment_attempts where student_id=? and submitted_at is not null order by submitted_at desc limit 1",Integer.class,u.getId());
   assertEquals(scores.isEmpty()?null:scores.get(0),d.getLatestAssessmentScore());
   System.out.println("STUDENT_READ_ONLY id="+u.getId()+" activity="+d.getStudyActivity());
  }
 }
}
