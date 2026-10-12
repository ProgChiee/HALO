package com.ptc.halo;
import com.ptc.halo.service.StudentStudyActivityService;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.Role;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class StudentStudyActivityTest {
 JdbcTemplate db;StudentStudyActivityService service;UserEntity student;
 @BeforeEach void setup(){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MariaDB;DB_CLOSE_DELAY=-1","sa","");db=new JdbcTemplate(ds);service=new StudentStudyActivityService(new NamedParameterJdbcTemplate(ds));
  for(String sql:new String[]{"create table lesson_study_days(student_id bigint,study_date date)","create table assessment_attempts(student_id bigint,started_at timestamp,submitted_at timestamp)","create table student_module_progress(student_id bigint,completed boolean,completed_at timestamp)","create table mentor_sessions(id bigint,student_id bigint)","create table mentor_messages(session_id bigint,sender varchar(20),message varchar(255),created_at timestamp)"})db.execute(sql);
  student=new UserEntity();student.setId(1L);student.setRole(Role.STUDENT);
 }
 void study(String date){db.update("insert into lesson_study_days values(1,?)",date);}
 @Test void mixedSourcesDeduplicateAndWeekBoundary(){
  study("2026-10-05");study("2026-10-05");
  db.update("insert into assessment_attempts values(1,'2026-10-05 09:00:00','2026-10-07 09:00:00')");
  db.update("insert into student_module_progress values(1,true,'2026-10-09 09:00:00')");
  db.update("insert into mentor_sessions values(1,1),(2,2)");db.update("insert into mentor_messages values(1,'STUDENT','Question','2026-10-11 09:00:00'),(1,'STUDENT','  ','2026-10-10 09:00:00'),(1,'HALO','Reply','2026-10-08 09:00:00'),(2,'STUDENT','Other','2026-10-08 09:00:00')");
  var value=service.get(student,LocalDate.of(2026,10,11));assertEquals(4,value.weeklyStudyDays());assertEquals(1,value.studyStreak());
  assertEquals(0,service.get(student,LocalDate.of(2026,10,12)).weeklyStudyDays());assertEquals(1,service.get(student,LocalDate.of(2026,10,12)).studyStreak());
 }
 @Test void consecutiveDaysTodayYesterdayAndExpired(){
  study("2026-10-09");study("2026-10-10");study("2026-10-11");study("2026-10-11");
  assertEquals(3,service.get(student,LocalDate.of(2026,10,11)).studyStreak());assertEquals(3,service.get(student,LocalDate.of(2026,10,12)).studyStreak());assertEquals(0,service.get(student,LocalDate.of(2026,10,13)).studyStreak());
 }
 @Test void gapBreaksAndMonthBoundaryDoesNot(){
  study("2026-09-27");study("2026-09-29");study("2026-09-30");study("2026-10-01");
  assertEquals(3,service.get(student,LocalDate.of(2026,10,1)).studyStreak());assertEquals(3,service.get(student,LocalDate.of(2026,10,1)).weeklyStudyDays());
 }
 @Test void calendarIsMonthlyDistinctAndExcludesFutureDates(){
  study("2026-09-30");study("2026-10-01");study("2026-10-01");study("2026-10-12");
  db.update("insert into assessment_attempts values(1,'2026-10-01 09:00:00','2026-10-03 09:00:00')");
  assertEquals(java.util.List.of(LocalDate.of(2026,10,1),LocalDate.of(2026,10,3)),service.calendar(student,"2026-10",LocalDate.of(2026,10,11)).activeDates());
  assertEquals(java.util.List.of(LocalDate.of(2026,9,30)),service.calendar(student,"2026-09",LocalDate.of(2026,10,11)).activeDates());
  assertTrue(service.calendar(student,"2026-11",LocalDate.of(2026,10,11)).activeDates().isEmpty());
  assertEquals("2026-10",service.calendar(student,null,LocalDate.of(2026,10,11)).month());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.calendar(student,"bad"));
 }
 @Test void mentorCountsDistinctMeaningfulSessionsWithinEachPeriod(){
  db.update("insert into mentor_sessions values(1,1),(2,1),(3,1),(4,2),(5,1)");
  for(int i=0;i<8;i++)db.update("insert into mentor_messages values(1,'STUDENT','Question','2026-10-05 10:00:00')");
  db.update("insert into mentor_messages values(2,'STUDENT','Question','2026-10-01 00:00:00'),(3,'HALO','Welcome','2026-10-06 10:00:00'),(3,'STUDENT','   ','2026-10-06 10:00:00'),(4,'STUDENT','Other student','2026-10-06 10:00:00'),(5,'STUDENT','Old','2026-09-30 23:59:59'),(5,'STUDENT','Future','2026-10-12 00:00:00')");
  var result=service.get(student,LocalDate.of(2026,10,11));assertEquals(1,result.mentorSessionsWeek());assertEquals(2,result.mentorSessionsMonth());
  assertEquals(0,service.get(student,LocalDate.of(2026,11,1)).mentorSessionsMonth());
 }
 @Test void zeroFutureAndOtherStudentAreExcluded(){
  assertEquals(0,service.get(student,LocalDate.of(2026,10,11)).weeklyStudyDays());study("2026-10-12");db.update("insert into lesson_study_days values(2,'2026-10-11')");
  assertEquals(0,service.get(student,LocalDate.of(2026,10,11)).studyStreak());assertEquals(0,service.get(student,LocalDate.of(2026,10,11)).weeklyStudyDays());
  student.setRole(Role.ADMIN);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.get(student));
 }
}
