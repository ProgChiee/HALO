package com.ptc.halo.service;

import java.text.Normalizer;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Shared server rule for editable profile names; Unicode letters and name punctuation. */
public final class FullNameValidator {
 private FullNameValidator() {}
 private static final Pattern SPACES=Pattern.compile("[\\p{IsWhite_Space}\\uFEFF]+");
 private static final Pattern CHARACTERS=Pattern.compile("[\\p{L}\\p{M} .,'\\u2019-]+");
 private static final Pattern LETTER=Pattern.compile("\\p{L}");
 public static String normalize(String value) {
  String name=SPACES.matcher(Normalizer.normalize(value==null?"":value,Normalizer.Form.NFC)).replaceAll(" ").strip();
  if(name.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"FULL_NAME_REQUIRED");
  if(name.length()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"FULL_NAME_TOO_LONG");
  if(!CHARACTERS.matcher(name).matches() || !LETTER.matcher(name).find())
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_FULL_NAME");
  return name;
 }
}
