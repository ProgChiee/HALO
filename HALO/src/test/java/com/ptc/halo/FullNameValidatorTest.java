package com.ptc.halo;
import com.ptc.halo.service.FullNameValidator;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class FullNameValidatorTest {
 @Test void legitimateNamesAndUnicodeAreAcceptedAndNormalized() {
  for(String name:new String[]{"Talidong, Archie V.","Dela Cruz, Juan P.","O'Connor, James","Anne-Marie Santos","Maria D. Reyes","Jos\u00e9 Ni\u00f1o","D\u2019Angelo"})
   assertEquals(name,FullNameValidator.normalize(name));
  assertEquals("Talidong, Archie V.",FullNameValidator.normalize("  Talidong,   Archie\tV.  "));
  assertEquals("Maria Reyes",FullNameValidator.normalize("\u00a0Maria\u00a0\u00a0Reyes\u00a0"));
 }
 @Test void unsafeBlankAndLongNamesAreRejectedSpecifically() {
  for(String name:new String[]{""," \t\n ","\u00a0"}) assertEquals("FULL_NAME_REQUIRED",assertThrows(ResponseStatusException.class,()->FullNameValidator.normalize(name)).getReason());
  assertEquals("FULL_NAME_TOO_LONG",assertThrows(ResponseStatusException.class,()->FullNameValidator.normalize("A".repeat(101))).getReason());
  for(String name:new String[]{"<script>alert('x')</script>","John\\Doe","John; DROP TABLE users","John\u0000Doe","...,---","Name123"})
   assertEquals("INVALID_FULL_NAME",assertThrows(ResponseStatusException.class,()->FullNameValidator.normalize(name)).getReason());
 }
}
