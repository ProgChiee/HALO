package com.ptc.halo;
import com.ptc.halo.dtoRequest.StudentRequest;
import com.ptc.halo.enums.YearLevel;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StudentRegistrationValidationTest {
 StudentRequest valid(){var r=new StudentRequest();r.setName(" Talidong,  Archie V. ");r.setEmail(" Archie@PATEROSTECHNOLOGICALCOLLEGE.EDU.PH ");r.setStudentId(" 2023-00123 ");r.setSection("BSHM 1A");r.setYearLevel(YearLevel.FIRST_YEAR);r.setPassword("password-123");return r;}
 @Test void validPunctuationAndHyphenRemainIntact(){var r=valid();StudentRegistrationValidator.validate(r);assertEquals("Talidong, Archie V.",r.getName());assertEquals("2023-00123",r.getStudentId());assertEquals("archie@paterostechnologicalcollege.edu.ph",r.getEmail());}
 @Test void invalidIdsAreRejected(){for(String id:new String[]{"202300123","2023-123","23-00123","2023--00123","2023-0012a",""}){var r=valid();r.setStudentId(id);assertTrue(assertThrows(RegistrationValidationException.class,()->StudentRegistrationValidator.validate(r)).errors.containsKey("studentId"));}}
 @Test void allFieldsAreValidatedBeforePersistence(){var r=valid();r.setName("<script>");r.setEmail("user@gmail.com");r.setSection(" ");r.setYearLevel(null);r.setPassword("short");var e=assertThrows(RegistrationValidationException.class,()->StudentRegistrationValidator.validate(r));assertEquals(5,e.errors.size());}
 @Test void passwordByteLimitAndBlankNames(){var r=valid();r.setPassword("x".repeat(73));r.setName("   ");assertEquals(2,assertThrows(RegistrationValidationException.class,()->StudentRegistrationValidator.validate(r)).errors.size());}
}
