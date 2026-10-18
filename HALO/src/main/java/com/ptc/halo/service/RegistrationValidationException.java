package com.ptc.halo.service;
import java.util.Map;
public class RegistrationValidationException extends RuntimeException {
 public final Map<String,String> errors; public final int status;
 public RegistrationValidationException(Map<String,String> errors,int status){super("Registration input rejected");this.errors=Map.copyOf(errors);this.status=status;}
}
