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
@Import(ProfessorDashboardService.class)
@Transactional(readOnly=true)
class ProfessorDashboardMariaDbLiveTest {
 @Autowired ProfessorDashboardService service;@Autowired UserRepository users;@Autowired JdbcTemplate jdbc;
 @Test void realDatabaseDashboardMatchesScopedActiveRosterAndReturnsBoundedSections(){
  assertTrue(jdbc.queryForObject("select version()",String.class).contains("MariaDB"));assertEquals(1,jdbc.queryForObject("select @@tx_read_only",Integer.class));
  var professors=users.findByRole(Role.PROFESSOR).stream().filter(p->p.getStatus()==Status.ACTIVE).toList();assertFalse(professors.isEmpty());
  for(var p:professors){
   var d=service.getDashboard(p);
   Long expected=jdbc.queryForObject("""
    select count(*) from user_entity u where u.role='STUDENT' and u.status='ACTIVE' and exists
    (select 1 from student_profile_entity sp where sp.user_id=u.id and exists
    (select 1 from subjects s join professor_entity pr on pr.id=s.professor_id where pr.user_id=? and s.year_level=sp.year_level))
    """,Long.class,p.getId());
   assertEquals(expected,d.getTotalStudents());
   assertEquals(expected,users.findProfessorProgressSummaries(p.getId(),java.util.List.of(Status.ACTIVE),org.springframework.data.domain.PageRequest.of(0,1)).getTotalElements());assertTrue(d.getPerformance().size()<=28);assertTrue(d.getRecentActivity().size()<=6);
   for(var item:d.getAttention())assertTrue(item.lessons().size()<=3);
   System.out.println("DASHBOARD_READ_ONLY professorId="+p.getId()+" scopedStudents="+d.getTotalStudents()+" performanceDays="+d.getPerformance().size()+" recentActivities="+d.getRecentActivity().size());
  }
 }
}
