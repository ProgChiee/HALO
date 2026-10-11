package com.ptc.halo;

import com.fasterxml.jackson.databind.JsonNode;
import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.*;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, read-only HTTP verification against the configured real MariaDB. No fixtures or DDL. */
@EnabledIfSystemProperty(named="halo.test.mariadb", matches="true")
@org.springframework.test.context.ActiveProfiles("monitoring-mariadb-live-test")
@SpringBootTest(classes=AdminMonitoringMariaDbLiveTest.Config.class,
    webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "server.address=127.0.0.1", "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=never", "spring.datasource.hikari.read-only=true",
        "spring.datasource.hikari.connection-init-sql=SET SESSION TRANSACTION READ ONLY",
        "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.springframework.web=INFO"})
class AdminMonitoringMariaDbLiveTest {
    // Only production monitoring/auth components: no bootstrap initializer or external AI/mail beans.
    @Configuration(proxyBeanMethods=false)
    @Profile("monitoring-mariadb-live-test")
    @EntityScan("com.ptc.halo.entity")
    @EnableJpaRepositories("com.ptc.halo.repository")
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
        CustomUserDetailsService.class, AdminProfessorMonitoringController.class,
        AdminStudentMonitoringController.class, AdminProfessorMonitoringService.class,
        AdminStudentMonitoringService.class, AdminValidationHandler.class})
    @ImportAutoConfiguration({
        org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration.class,
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
        org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class,
        org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class,
        org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration.class,
        org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration.class,
        org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class,
        org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration.class})
    static class Config {}

    @DynamicPropertySource static void isolatedSigningKey(DynamicPropertyRegistry registry) {
        byte[] key=new byte[32]; new SecureRandom().nextBytes(key);
        registry.add("jwt.secret", () -> Base64.getEncoder().encodeToString(key));
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    HttpEntity<Void> request;

    @BeforeEach void authenticateExistingAdmin() {
        assertTrue(jdbc.queryForObject("select version()",String.class).contains("MariaDB"));
        assertEquals(1,jdbc.queryForObject("select @@tx_read_only",Integer.class));
        Long id=jdbc.queryForObject("select id from user_entity where role='ADMIN' and status='ACTIVE' and must_change_password=false order by id limit 1",Long.class);
        var headers=new HttpHeaders(); headers.setBearerAuth(jwt.generateToken(users.findById(id).orElseThrow()));
        request=new HttpEntity<>(headers); // Token is test-server-only and is never printed or persisted.
    }
    JsonNode page(String role,int page) {
        var response=http.exchange("/api/admin/"+role+"/monitoring?page="+page+"&size=2",HttpMethod.GET,request,JsonNode.class);
        assertEquals(HttpStatus.OK,response.getStatusCode(),role+" monitoring must return HTTP 200");
        return Objects.requireNonNull(response.getBody());
    }
    long count(String sql,Object...args) { return jdbc.queryForObject(sql,Long.class,args); }
    void value(Object expected,JsonNode actual) {
        if(expected==null) assertTrue(actual==null || actual.isNull());
        else if(expected instanceof LocalDateTime time) assertEquals(time,LocalDateTime.parse(actual.asText()));
        else assertEquals(expected.toString(),actual.asText());
    }
    void pages(String role,List<Map<String,Object>> expected,List<Map<String,Object>> highlights,java.util.function.Consumer<JsonNode> summary) {
        int pageCount=Math.max(1,(expected.size()+1)/2);
        for(int p=0;p<pageCount;p++) {
            var result=page(role,p); assertEquals(p,result.path("number").asInt());assertEquals(2,result.path("size").asInt());
            assertEquals(expected.size(),result.path("totalElements").asInt());assertEquals((expected.size()+1)/2,result.path("totalPages").asInt());
            var rows=result.path("content");assertEquals(Math.min(2,expected.size()-p*2),rows.size());
            for(int i=0;i<rows.size();i++) for(var field:expected.get(p*2+i).entrySet()) value(field.getValue(),rows.get(i).get(field.getKey()));
            summary.accept(result.path("summary"));
            assertEquals(Math.min(8,highlights.size()),result.path("highlights").size());
            for(int i=0;i<Math.min(8,highlights.size());i++) for(var field:highlights.get(i).entrySet()) value(field.getValue(),result.path("highlights").get(i).get(field.getKey()));
        }
        assertEquals(0,page(role,pageCount).path("content").size());
        System.out.println("LIVE MariaDB "+role+" monitoring: HTTP 200; "+expected.size()+" real accounts; every page, row and summary verified");
    }
    @Test void professorActivityMatchesRealDatabase() {
        var expected=jdbc.queryForList("select u.id as userId,p.professor_id as professorId,u.name,u.email,u.status from professor_entity p join user_entity u on u.id=p.user_id where u.role='PROFESSOR'");
        long totalActivities=0,active=0;
        for(var row:expected) {
            long activities=count("select count(*) from activity_log_entity where user_id=? and activity_type='MODULE'",row.get("userId"));
            row.put("moduleActivities",activities);totalActivities+=activities;
            row.put("lastActivity",jdbc.queryForObject("select max(created_at) from activity_log_entity where user_id=?",LocalDateTime.class,row.get("userId")));
            if("ACTIVE".equals(row.get("status")))active++;
        }
        expected.sort(Comparator.<Map<String,Object>>comparingLong(r->((Number)r.get("moduleActivities")).longValue()).reversed().thenComparingLong(r->((Number)r.get("userId")).longValue()));
        var highlights=new ArrayList<>(expected);
        highlights.sort(Comparator.<Map<String,Object>,LocalDateTime>comparing(r->(LocalDateTime)r.get("lastActivity"),Comparator.nullsLast(Comparator.reverseOrder())).thenComparingLong(r->((Number)r.get("userId")).longValue()));
        long finalActivities=totalActivities,finalActive=active;
        pages("professors",expected,highlights,s->{assertEquals(expected.size(),s.path("total").asLong());assertEquals(finalActive,s.path("active").asLong());assertEquals(finalActivities,s.path("moduleActivities").asLong());});
    }
    @Test void studentPerformanceMatchesRealDatabase() {
        var expected=jdbc.queryForList("select u.id as userId,p.student_id as studentId,u.name,u.email,u.status,p.section,p.year_level as yearLevel from student_profile_entity p join user_entity u on u.id=p.user_id where u.role='STUDENT'");
        long passed=0,scored=0;double scoreSum=0;
        for(var row:expected) {
            Object id=row.get("userId");
            row.put("completedModules",count("select count(*) from student_module_progress where student_id=? and completed=true",id));
            long passes=count("select count(*) from assessment_attempts where student_id=? and passed=true",id);row.put("passedAssessments",passes);passed+=passes;
            row.put("totalBadges",count("select count(*) from student_badges where student_id=?",id));
            var latest=jdbc.queryForList("select score from assessment_attempts where student_id=? and submitted_at is not null order by submitted_at desc,id desc limit 1",Integer.class,id);
            Integer score=latest.isEmpty()?null:latest.get(0);row.put("latestAssessmentScore",score);
            if(score!=null){scored++;scoreSum+=score;}
        }
        expected.sort(Comparator.<Map<String,Object>,Integer>comparing(r->(Integer)r.get("latestAssessmentScore"),Comparator.nullsLast(Comparator.reverseOrder())).thenComparingLong(r->((Number)r.get("userId")).longValue()));
        var highlights=new ArrayList<>(expected.stream().filter(r->r.get("latestAssessmentScore")!=null).toList());
        highlights.sort(Comparator.<Map<String,Object>>comparingInt(r->(Integer)r.get("latestAssessmentScore")).thenComparingLong(r->((Number)r.get("userId")).longValue()));
        long finalPassed=passed,finalScored=scored;double average=scored==0?0:scoreSum/scored;
        // MariaDB AVG(integer) returns a decimal rounded to four fractional digits by default.
        pages("students",expected,highlights,s->{assertEquals(expected.size(),s.path("total").asLong());assertEquals(finalScored,s.path("scored").asLong());assertEquals(finalPassed,s.path("passedAssessments").asLong());if(finalScored==0)assertTrue(s.path("averageScore").isNull());else assertEquals(average,s.path("averageScore").asDouble(),0.0001);});
    }
}
