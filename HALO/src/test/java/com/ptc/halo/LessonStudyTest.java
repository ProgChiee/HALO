package com.ptc.halo;
import com.ptc.halo.service.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@DataJpaTest(properties="spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",showSql=false)
@Import(LessonStudyService.class)
class LessonStudyTest {
 @Autowired LessonStudyService service;
 @Autowired LessonStudyDayRepository days;
 @Autowired UserRepository users;
 @MockBean StudentLessonService lessons;
 UserEntity student;
 @BeforeEach void setup(){
  var u=new UserEntity();u.setName("Student");u.setEmail("study@example.test");u.setRole(Role.STUDENT);u.setStatus(Status.ACTIVE);student=users.saveAndFlush(u);
  var lesson=new com.ptc.halo.dtoResponse.AiLearningModuleResponse();lesson.setId(7L);
  when(lessons.byWeek(3L,student)).thenReturn(lesson);
 }
 @Test void repeatedOpenStoresOneDayAndRetainsEarlierDays(){
  days.saveAndFlush(new LessonStudyDayEntity(student.getId(),7L,LocalDate.now().minusDays(2)));
  service.record(3L,student);service.record(3L,student);
  assertEquals(2,days.count());assertTrue(days.existsByStudentIdAndModuleIdAndStudyDate(student.getId(),7L,LocalDate.now()));verify(lessons,times(2)).byWeek(3L,student);
 }
 @Test void deniedLessonNeverCreatesStudyRow(){
  when(lessons.byWeek(3L,student)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"STUDENT_NOT_ENROLLED"));
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.record(3L,student));assertEquals(0,days.count());
 }
 @Test void databaseUniquenessRejectsDirectDuplicates(){
  service.record(3L,student);
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->days.saveAndFlush(new LessonStudyDayEntity(student.getId(),7L,LocalDate.now())));
 }
}
