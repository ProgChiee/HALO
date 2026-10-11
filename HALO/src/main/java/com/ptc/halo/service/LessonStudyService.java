package com.ptc.halo.service;
import com.ptc.halo.entity.*;
import com.ptc.halo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class LessonStudyService {
 private final UserRepository users;
 private final StudentLessonService lessons;
 private final LessonStudyDayRepository days;
 private java.time.Clock clock=java.time.Clock.systemDefaultZone();
 public LessonStudyService(UserRepository users,StudentLessonService lessons,LessonStudyDayRepository days){this.users=users;this.lessons=lessons;this.days=days;}
 @Transactional
 public Long record(Long weekId,UserEntity student){
  // Same per-student lock as quiz/progress mutations; retries cannot race the daily insert.
  var current=users.findForAssessmentLifecycle(student.getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"STUDENT_NOT_FOUND"));
  var lesson=lessons.byWeek(weekId,current); // role, active account, publication, year and progression checks first
  var now=java.time.LocalDateTime.now(clock);
  var today=now.toLocalDate();
  var previous=days.findTopByStudentIdAndModuleIdOrderByStudyDateDesc(current.getId(),lesson.getId()).orElse(null);
  // A refresh extends the inactivity window. Midnight alone does not create a review.
  boolean review=previous!=null && (previous.getLastOpenedAt()!=null
    ? !now.isBefore(previous.getLastOpenedAt().plusMinutes(30))
    : previous.getStudyDate().isBefore(today));
  var day=previous!=null && previous.getStudyDate().equals(today) ? previous : new LessonStudyDayEntity(current.getId(),lesson.getId(),today);
  if(review)day.addReview();
  if(day.getLastOpenedAt()==null || now.isAfter(day.getLastOpenedAt()))day.setLastOpenedAt(now);
  days.saveAndFlush(day);
  return lesson.getId();
 }
}
