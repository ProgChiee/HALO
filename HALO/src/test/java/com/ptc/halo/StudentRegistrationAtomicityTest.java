package com.ptc.halo;

import com.ptc.halo.dtoRequest.StudentRequest;
import com.ptc.halo.enums.YearLevel;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import com.ptc.halo.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"},showSql=false)
@Import(AuthService.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class StudentRegistrationAtomicityTest {
 @Autowired AuthService auth;
 @Autowired UserRepository users;
 @org.springframework.boot.test.mock.mockito.SpyBean StudentProfileRepository profiles;
 @MockBean AuthenticationManager authentication;
 @MockBean JwtService jwt;
 @MockBean PasswordEncoder encoder;
 @MockBean AuthAuditService audit;
 StudentRequest request(String email,String studentId) {
  var r=new StudentRequest();r.setName("Student");r.setEmail(email+"@paterostechnologicalcollege.edu.ph");
  r.setPassword("test-password");r.setStudentId(studentId);r.setSection("A");r.setYearLevel(YearLevel.FIRST_YEAR);return r;
 }
 @Test void registrationCommitsBothRecordsOrNeither() {
  var first=request("atomic-first","2023-00123");auth.registerStudent(first);
  var user=users.findByEmail(first.getEmail()).orElseThrow();
  assertTrue(profiles.findByUserId(user.getId()).isPresent());
  var duplicate=request("atomic-duplicate","2023-00123");
  assertThrows(RegistrationValidationException.class,()->auth.registerStudent(duplicate));
  assertFalse(users.existsByEmail(duplicate.getEmail()));
  var missing=request("atomic-missing",null);
  assertThrows(RegistrationValidationException.class,()->auth.registerStudent(missing));
  assertFalse(users.existsByEmail(missing.getEmail()));
  assertTrue(profiles.findByUserId(user.getId()).isPresent());
 }

 @Test void profileFailureRollsBackUserInsert(){
  var r=request("atomic-rollback","2023-00999");
  org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("simulated concurrent conflict")).when(profiles).save(org.mockito.ArgumentMatchers.any());
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->auth.registerStudent(r));
  assertFalse(users.existsByEmail(r.getEmail()));
 }
 @Test void duplicateEmailIsReportedWithoutCreatingAnotherProfile(){
  var r=request("atomic-email","2023-00888");auth.registerStudent(r);
  var duplicate=request("atomic-email","2023-00889");
  assertTrue(assertThrows(RegistrationValidationException.class,()->auth.registerStudent(duplicate)).errors.containsKey("email"));
  assertFalse(profiles.existsByStudentId("2023-00889"));
 }
}
