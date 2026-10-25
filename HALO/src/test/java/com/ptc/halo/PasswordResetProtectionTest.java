package com.ptc.halo;

import com.ptc.halo.service.PasswordResetProtection;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordResetProtectionTest {
 PasswordResetProtection policy(String key){return new PasswordResetProtection(key,5,600,60,3600,3,20,10,100);}
 @Test void deploymentMustProvideAValidIndependentSecret(){
  for(String value:new String[]{"","not-base64",Base64.getEncoder().encodeToString(new byte[16])})
   assertThrows(IllegalStateException.class,()->policy(value));
 }
 @Test void verifiersAreSaltedUserBoundAndFailClosed(){
  var protection=policy(Base64.getEncoder().encodeToString(new byte[32]));
  String first=protection.verifier(1,"123456"),second=protection.verifier(1,"123456");
  assertNotEquals(first,second);assertTrue(protection.matches(1,"123456",first));
  assertFalse(protection.matches(2,"123456",first));assertFalse(protection.matches(1,"000000",first));
  assertFalse(protection.matches(1,"123456","123456"));assertFalse(protection.matches(1,"123456",null));
  assertFalse(protection.matches(1,"123456","v1:invalid:value"));
  byte[] different=new byte[32];different[0]=1;assertFalse(policy(Base64.getEncoder().encodeToString(different)).matches(1,"123456",first));
  assertEquals(64,protection.bucket("request:account","student@example.test").length());
  assertNotEquals(protection.bucket("request:account","student@example.test"),protection.bucket("verify:account","student@example.test"));
 }
 @Test void limitsMustStayWithinSafeConfigurationBounds(){
  assertThrows(IllegalStateException.class,()->new PasswordResetProtection(Base64.getEncoder().encodeToString(new byte[32]),0,600,60,3600,3,20,10,100));
 }
}
