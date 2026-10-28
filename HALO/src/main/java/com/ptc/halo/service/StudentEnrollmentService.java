package com.ptc.halo.service;
import com.ptc.halo.dtoResponse.StudentSubjectResponse;
import com.ptc.halo.entity.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.security.AuthenticatedAccount;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service
@Transactional(readOnly=true)
public class StudentEnrollmentService {
 private final StudentSubjectEnrollmentRepository enrollments;
 private final StudentProfileRepository profiles;
 private final SubjectRepository subjects;
 private final UserRepository users;
 private final StudentSubjectService summaries;
 private final StudentLessonAccessService access;
 private final ActivityLogService activity;
 public StudentEnrollmentService(StudentSubjectEnrollmentRepository enrollments,StudentProfileRepository profiles,SubjectRepository subjects,UserRepository users,StudentSubjectService summaries,StudentLessonAccessService access,ActivityLogService activity){this.enrollments=enrollments;this.profiles=profiles;this.subjects=subjects;this.users=users;this.summaries=summaries;this.access=access;this.activity=activity;}
 private List<StudentSubjectResponse> eligible(UserEntity student){
  access.validateStudent(student);
  var profile=profiles.findByUserId(student.getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"PROFILE_INCOMPLETE"));
  if(profile.getYearLevel()==null || profile.getStudentId()==null || profile.getStudentId().isBlank() || profile.getSection()==null || profile.getSection().isBlank())
   throw new ResponseStatusException(HttpStatus.CONFLICT,"PROFILE_INCOMPLETE");
  // Reuse published/generated module eligibility and unchanged progress calculation.
  return summaries.getStudentSubjects(student).stream().filter(s->s.getEligibleModuleCount()!=null && s.getEligibleModuleCount()>0).toList();
 }
 public List<StudentSubjectResponse> list(UserEntity student,boolean mine){
  var available=eligible(student);var ids=new HashSet<>(enrollments.subjectIds(student.getId()));
  return available.stream().filter(s->ids.contains(s.getSubjectId())==mine).sorted(Comparator.comparing(StudentSubjectResponse::getSubjectId)).toList();
 }
 @Transactional
 public void enroll(Authentication authentication,Long subjectId){
  // The same user lock serializes retries and lifecycle changes across backend instances.
  var student=AuthenticatedAccount.lock(users,authentication);
  var eligible=eligible(student);
  if(eligible.stream().noneMatch(s->s.getSubjectId().equals(subjectId)))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"SUBJECT_UNAVAILABLE");
  if(enrollments.existsByStudent_IdAndSubject_Id(student.getId(),subjectId))return;
  var subject=subjects.findById(subjectId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"SUBJECT_UNAVAILABLE"));
  enrollments.saveAndFlush(new StudentSubjectEnrollmentEntity(student,subject));
  activity.createLog(student,com.ptc.halo.enums.ActivityType.ACCOUNT,"Enrolled in subject "+subject.getSubjectCode());
 }
}
