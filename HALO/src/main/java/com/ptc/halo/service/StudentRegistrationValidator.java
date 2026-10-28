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
  var academic = normalizeAcademic(r.getStudentId(), r.getSection(), r.getYearLevel(), errors);
  r.setStudentId(academic.studentId()); r.setSection(academic.section());
  String password=r.getPassword();
  if(password==null || password.isBlank() || password.length()<8 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)errors.put("password","Use at least 8 characters and no more than 72 UTF-8 bytes.");
  if(!errors.isEmpty())throw new RegistrationValidationException(errors,400);
 }

 public record AcademicDetails(String studentId, String section) {}
 public static AcademicDetails validateAcademic(String studentId, String section, com.ptc.halo.enums.YearLevel year) {
  var errors = new LinkedHashMap<String,String>();
  var result = normalizeAcademic(studentId, section, year, errors);
  if (!errors.isEmpty()) throw new RegistrationValidationException(errors,400);
  return result;
 }
 private static AcademicDetails normalizeAcademic(String id, String section, com.ptc.halo.enums.YearLevel year, java.util.Map<String,String> errors) {
  try { id = validateStudentId(id); } catch (RegistrationValidationException e) { errors.putAll(e.errors); }
  try { section = validateSection(section); } catch (RegistrationValidationException e) { errors.putAll(e.errors); }
  if (year == null) errors.put("yearLevel","Select your year level.");
  return new AcademicDetails(id, section);
 }
 public static String validateStudentId(String id) {
  id = id == null ? "" : id.strip();
  if (!id.matches("[0-9]{2}AHRT-[0-9]{4}")) throw new RegistrationValidationException(java.util.Map.of("studentId","Use 2 digits + AHRT + hyphen + 4 digits, for example 29AHRT-0168. Include the hyphen."),400);
  return id;
 }
 public static String validateSection(String section) {
  section = section == null ? "" : section.strip().replaceAll("\\s+", " ");
  if (section.isEmpty() || section.length()>50 || !section.matches("[\\p{L}\\p{M}\\p{N} .'-]+")) throw new RegistrationValidationException(java.util.Map.of("section","Enter a section of up to 50 characters, for example BSHM 1A."),400);
  return section;
 }
}
