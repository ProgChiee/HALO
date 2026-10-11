package com.ptc.halo.service;
import com.ptc.halo.entity.*;
import com.ptc.halo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
@Service
public class LessonStudyService {
 private final UserRepository users;
 private final StudentLessonService lessons;
 private final LessonStudyDayRepository days;
 public LessonStudyService(UserRepository users,StudentLessonService lessons,LessonStudyDayRepository days){this.users=users;this.lessons=lessons;this.days=days;}
 @Transactional
 public Long record(Long weekId,UserEntity student){
  // Same per-student lock as quiz/progress mutations; retries cannot race the daily insert.
  var current=users.findForAssessmentLifecycle(student.getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"STUDENT_NOT_FOUND"));
  var lesson=lessons.byWeek(weekId,current); // role, active account, publication, year and progression checks first
  var today=LocalDate.now();
  if(!days.existsByStudentIdAndModuleIdAndStudyDate(current.getId(),lesson.getId(),today))
   days.saveAndFlush(new LessonStudyDayEntity(current.getId(),lesson.getId(),today));
  return lesson.getId();
 }
}
