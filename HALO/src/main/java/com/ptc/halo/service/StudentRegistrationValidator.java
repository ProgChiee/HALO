package com.ptc.halo.service;
import com.ptc.halo.dtoRequest.StudentRequest;
import java.util.LinkedHashMap;
import java.util.Locale;
public final class StudentRegistrationValidator {
 private StudentRegistrationValidator() {}
 public static void validate(StudentRequest r){
  var errors=new LinkedHashMap<String,String>();
  try {r.setName(FullNameValidator.normalize(r.getName()));}
  catch(org.springframework.web.server.ResponseStatusException e){errors.put("name","Enter a name of 1-100 characters using letters, spaces, hyphens, apostrophes, periods or commas.");}
  String email=r.getEmail()==null?"":r.getEmail().strip().toLowerCase(Locale.ROOT);
  r.setEmail(email);
  if(email.length()>254 || !email.matches("[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@paterostechnologicalcollege\\.edu\\.ph")) errors.put("email","Use your @paterostechnologicalcollege.edu.ph school email.");
  r.setStudentId(r.getStudentId()==null?"":r.getStudentId().strip());
  if(!r.getStudentId().matches("[0-9]{4}-[0-9]{5}")) errors.put("studentId","Use YYYY-NNNNN, for example 2023-00123. Include the hyphen.");
  String section=r.getSection()==null?"":r.getSection().strip().replaceAll("\\s+"," ");r.setSection(section);
  if(section.isEmpty() || section.length()>50 || !section.matches("[\\p{L}\\p{M}\\p{N} .'-]+")) errors.put("section","Enter a section of up to 50 characters, for example BSHM 1A.");
  if(r.getYearLevel()==null)errors.put("yearLevel","Select your year level.");
  String password=r.getPassword();
  if(password==null || password.isBlank() || password.length()<8 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)errors.put("password","Use at least 8 characters and no more than 72 UTF-8 bytes.");
  if(!errors.isEmpty())throw new RegistrationValidationException(errors,400);
 }
}
