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
  if(mine)return mySubjects(student);
  var available=eligible(student);var ids=new HashSet<>(enrollments.subjectIds(student.getId()));
  return available.stream().filter(s->ids.contains(s.getSubjectId())==mine).sorted(Comparator.comparing(StudentSubjectResponse::getSubjectId)).toList();
 }
 private List<StudentSubjectResponse> mySubjects(UserEntity student){
  access.validateStudent(student);
  // Membership is independent of current eligibility. Fetch metadata in bulk, never lesson content.
  var owned=enrollments.findActiveSubjects(student.getId());
  if(owned.stream().anyMatch(Objects::isNull)){
   org.slf4j.LoggerFactory.getLogger(StudentEnrollmentService.class).error("Active enrollment references a missing subject");
   throw new ResponseStatusException(HttpStatus.CONFLICT,"ENROLLMENT_INTEGRITY_ERROR");
  }
  var profile=profiles.findByUserId(student.getId()).orElse(null);
  var result=summaries.summarizeSubjects(student,owned);
  for(var subject:result){
   String reason=null;
   if(profile==null || profile.getYearLevel()==null)reason="PROFILE_INCOMPLETE";
   else if(profile.getYearLevel()!=subject.getYearLevel())reason="YEAR_LEVEL_MISMATCH";
   else if(subject.getEligibleModuleCount()==null || subject.getEligibleModuleCount()==0)reason="NO_PUBLISHED_LESSONS";
   subject.setEnrolled(true);
   subject.setLearningAvailable(reason==null);
   subject.setLearningUnavailableReason(reason);
   if(reason!=null){subject.setProgressPercentage(null);subject.setCompletedWeeks(null);}
  }
  return result.stream().sorted(Comparator.comparing(StudentSubjectResponse::getSubjectId)).toList();
 }
 @Transactional
 public void enroll(Authentication authentication,Long subjectId){
  validateId(subjectId);
  // The same user lock serializes retries and lifecycle changes across backend instances.
  var student=AuthenticatedAccount.lock(users,authentication);
  var eligible=eligible(student);
  if(eligible.stream().noneMatch(s->s.getSubjectId().equals(subjectId)))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"SUBJECT_UNAVAILABLE");
  var existing=enrollments.findForUpdate(student.getId(),subjectId);
  if(existing.isPresent()){
   if(existing.get().isActive())return;
   existing.get().reactivate();
   enrollments.saveAndFlush(existing.get());
   activity.createLog(student,com.ptc.halo.enums.ActivityType.ACCOUNT,"Re-enrolled in subject ID "+subjectId);
   return;
  }
  var subject=subjects.findById(subjectId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"SUBJECT_UNAVAILABLE"));
  enrollments.saveAndFlush(new StudentSubjectEnrollmentEntity(student,subject));
  activity.createLog(student,com.ptc.halo.enums.ActivityType.ACCOUNT,"Enrolled in subject "+subject.getSubjectCode());
 }
 @Transactional
 public void unenroll(Authentication authentication,Long subjectId){
  validateId(subjectId);
  var student=AuthenticatedAccount.lock(users,authentication);
  access.validateStudent(student);
  // A Student can leave their own enrollment even if their academic eligibility has changed.
  var enrollment=enrollments.findForUpdate(student.getId(),subjectId)
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"ENROLLMENT_NOT_FOUND"));
  if(!enrollment.isActive())return;
  enrollment.deactivate();
  enrollments.saveAndFlush(enrollment);
  activity.createLog(student,com.ptc.halo.enums.ActivityType.ACCOUNT,"Unenrolled from subject ID "+subjectId);
 }
 private void validateId(Long subjectId){if(subjectId==null || subjectId<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_SUBJECT_ID");}
}
