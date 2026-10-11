package com.ptc.halo;
import com.ptc.halo.service.ProfessorEngagementService;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.Role;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ProfessorEngagementTest {
 JdbcTemplate db;ProfessorEngagementService service;UserEntity professor;
 @BeforeEach void setup(){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MariaDB;DB_CLOSE_DELAY=-1","sa","");db=new JdbcTemplate(ds);service=new ProfessorEngagementService(new NamedParameterJdbcTemplate(ds));
  for(String sql:new String[]{
   "create table user_entity(id bigint primary key,name varchar(100),role varchar(30))",
   "create table student_profile_entity(user_id bigint,year_level varchar(30))",
   "create table lesson_study_days(student_id bigint,module_id bigint,study_date date)",
   "create table professor_entity(id bigint primary key,user_id bigint)",
   "create table subjects(id bigint primary key,professor_id bigint,year_level varchar(30))",
   "create table weeks(id bigint primary key,subject_id bigint)",
   "create table ai_learning_modules(id bigint primary key,week_id bigint)",
   "create table assessments(id bigint primary key,module_id bigint)",
   "create table assessment_attempts(student_id bigint,assessment_id bigint,started_at timestamp,submitted_at timestamp)",
   "create table student_module_progress(student_id bigint,module_id bigint,completed boolean,completed_at timestamp)",
   "create table mentor_sessions(id bigint primary key,student_id bigint,ai_learning_module_id bigint)",
   "create table mentor_messages(session_id bigint,sender varchar(30),message varchar(200),created_at timestamp)"})db.execute(sql);
  db.update("insert into user_entity values(1,'Professor','PROFESSOR'),(2,'Other','PROFESSOR'),(10,'Student','STUDENT'),(11,'Unrelated','STUDENT'),(12,'Admin','ADMIN')");
  db.update("insert into professor_entity values(1,1),(2,2)");db.update("insert into subjects values(1,1,'FIRST_YEAR'),(2,2,'SECOND_YEAR')");db.update("insert into weeks values(1,1),(2,2)");db.update("insert into ai_learning_modules values(1,1),(2,2)");db.update("insert into assessments values(1,1),(2,2)");
  db.update("insert into student_profile_entity values(10,'FIRST_YEAR'),(11,'SECOND_YEAR'),(12,'FIRST_YEAR')");
  professor=db.queryForObject("select id from user_entity where id=1",(rs,n)->{var p=new UserEntity();p.setId(rs.getLong(1));p.setRole(Role.PROFESSOR);return p;});
 }
 void attempt(int student,int assessment,String start,String finish){db.update("insert into assessment_attempts values(?,?,?,?)",student,assessment,start,finish);}
 void message(int session,String sender,String text,String at){db.update("insert into mentor_messages values(?,?,?,?)",session,sender,text,at);}
 ProfessorEngagementService.Page page(){return service.get(professor,0,20,LocalDate.of(2026,10,11));}
 @Test void distinctDaysAndSessionsAreScopedAndBounded(){
  attempt(10,1,"2026-10-05 08:00:00","2026-10-05 09:00:00");
  db.update("insert into student_module_progress values(10,1,true,'2026-10-05 10:00:00')");
  db.update("insert into mentor_sessions values(1,10,1),(2,10,1),(3,10,1),(4,10,2),(5,11,2)");
  for(int i=0;i<8;i++)message(1,"STUDENT","Question","2026-10-07 12:00:00");
  message(2,"STUDENT","Question","2026-10-09 12:00:00");message(3,"HALO","Welcome","2026-10-10 12:00:00");message(3,"STUDENT","   ","2026-10-10 12:00:00");
  message(4,"STUDENT","Other subject","2026-10-06 12:00:00");message(5,"STUDENT","Other professor","2026-10-06 12:00:00");
  attempt(12,1,"2026-10-08 00:00:00",null);
  var page=page();assertEquals(1,page.totalElements());var m=page.content().get(0);
  assertEquals(3,m.activeDaysWeek());assertEquals(3,m.activeDaysMonth());assertEquals(2,m.mentorSessionsWeek());assertEquals(2,m.mentorSessionsMonth());
 }
 @Test void calendarBoundariesAndSessionsReusedAcrossPeriods(){
  db.update("insert into mentor_sessions values(1,10,1)");
  message(1,"STUDENT","Question","2026-09-30 23:59:59");message(1,"STUDENT","Question","2026-10-01 00:00:00");message(1,"STUDENT","Question","2026-10-04 23:59:59");message(1,"STUDENT","Question","2026-10-05 00:00:00");message(1,"STUDENT","Future","2026-10-12 00:00:00");
  var m=page().content().get(0);assertEquals(1,m.activeDaysWeek());assertEquals(3,m.activeDaysMonth());assertEquals(1,m.mentorSessionsWeek());assertEquals(1,m.mentorSessionsMonth());
  var november=service.get(professor,0,20,LocalDate.of(2026,11,1)).content().get(0);assertEquals(0,november.activeDaysMonth());assertEquals(0,november.mentorSessionsMonth());
 }
 @Test void zeroCountsForOldScopedActivityAndNoUnrelatedStudents(){
  attempt(10,1,"2025-01-01 00:00:00",null);attempt(11,2,"2026-10-06 00:00:00",null);
  assertEquals(10L,page().content().get(0).userId());assertEquals(0,page().content().get(0).activeDaysMonth());
  professor.setId(3L);assertTrue(page().content().isEmpty());
 }
 @Test void emptySessionsAndTemporaryPreviewDoNotCreateEngagement(){
  db.update("insert into mentor_sessions values(1,10,1)");message(1,"HALO","Welcome","2026-10-06 00:00:00");
  assertEquals(1,page().content().size());assertEquals(0,page().content().get(0).activeDaysWeek());assertEquals(0,page().content().get(0).mentorSessionsMonth()); // Preview exchanges are in-memory only, never these durable tables.
 }
 @Test void dailyStudyHistoryIncludesRepeatedVisitsAndCrossMonthWeek() {
  db.update("insert into lesson_study_days values(10,1,'2026-10-26'),(10,1,'2026-10-28'),(10,1,'2026-11-01'),(10,2,'2026-10-30')");
  attempt(10,1,"2026-11-01 12:00:00",null);
  var result=service.get(professor,0,20,LocalDate.of(2026,11,1)).content().get(0);
  assertEquals(3,result.activeDaysWeek());assertEquals(1,result.activeDaysMonth());assertEquals(0,result.mentorSessionsWeek());
 }
 @Test void paginationAndRoleGuard(){
  db.update("update student_profile_entity set year_level='FIRST_YEAR' where user_id=11");
  attempt(10,1,"2026-10-06 00:00:00",null);attempt(11,1,"2026-10-06 00:00:00",null);
  assertEquals(1,service.get(professor,0,1,LocalDate.of(2026,10,11)).content().size());assertEquals(2,service.get(professor,0,1,LocalDate.of(2026,10,11)).totalPages());
  assertEquals(11L,service.get(professor,1,1,LocalDate.of(2026,10,11)).content().get(0).userId());assertEquals(100,service.get(professor,0,1000).size());
  professor.setRole(Role.ADMIN);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->page());
 }
}
