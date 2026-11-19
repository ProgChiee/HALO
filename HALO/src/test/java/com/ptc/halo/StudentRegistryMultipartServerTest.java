package com.ptc.halo;

import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.StudentRegistryController;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes=StudentRegistryMultipartServerTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"server.address=127.0.0.1","jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","spring.servlet.multipart.max-file-size=3MB","spring.servlet.multipart.max-request-size=32MB"})
class StudentRegistryMultipartServerTest {
 @Configuration(proxyBeanMethods=false)
 @Import({SecurityConfig.class,JwtAuthenticationFilter.class,JwtService.class,StudentRegistryController.class,com.ptc.halo.controller.ApiMethodExceptionHandler.class})
 @ImportAutoConfiguration({org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration.class,org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration.class,
  org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration.class,
  org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
  org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration.class,
  org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration.class,
  org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class,
  org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class,
  org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
  org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class})
 static class Config {}
 @LocalServerPort int port;@Autowired JwtService jwt;
 @MockBean StudentRegistryService registry;@MockBean StudentRegistryWorkbook workbook;@MockBean UserRepository users;@MockBean CustomUserDetailsService details;
 String token(Role role) {
  var user=new UserEntity();user.setId(71L);user.setEmail("synthetic@example.test");user.setName("Synthetic Admin");user.setRole(role);user.setStatus(Status.ACTIVE);
  when(details.loadUserById(71L)).thenReturn(new CustomUserDetails(user));return jwt.generateToken(user);
 }
 HttpResponse<String> post(String token,byte[] file)throws Exception {
  String boundary="HALOSyntheticBoundary";
  byte[] prefix=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"fixture.xlsx\"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n").getBytes(StandardCharsets.UTF_8);
  var body=HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(prefix),HttpRequest.BodyPublishers.ofByteArray(file),HttpRequest.BodyPublishers.ofString("\r\n--"+boundary+"--\r\n"));
  var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/admin/student-registry/preview")).header("Content-Type","multipart/form-data; boundary="+boundary).POST(body);
  if(token!=null)request.header("Authorization","Bearer "+token);
  return HttpClient.newHttpClient().send(request.build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void workbookValidationMustNotBecomeAnAuthenticationFailure()throws Exception {
  when(registry.preview(any())).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_REGISTRY_WORKBOOK"));
  var response=post(token(Role.ADMIN),new byte[]{1,2,3});
  assertEquals(400,response.statusCode(),response.body());
 }

 @Test void authenticatedMultipartSucceedsAndOnlyPreviews()throws Exception {
  when(registry.preview(any())).thenAnswer(call->{
   org.springframework.web.multipart.MultipartFile file=call.getArgument(0);
   assertEquals("fixture.xlsx",file.getOriginalFilename());assertTrue(file.getSize()>0);
   return new StudentRegistryService.Preview(1,1,0,0,0,java.util.List.of());
  });
  var response=post(token(Role.ADMIN),new StudentRegistryWorkbook().template());
  assertEquals(200,response.statusCode());assertTrue(response.body().contains("\"eligible\":1"));
  verify(registry).preview(any());
  verify(registry,never()).confirm(any(),any());verifyNoInteractions(users);
 }
 @Test void emailHyperlinkWorkbookSurvivesRealAuthenticatedMultipartTransport()throws Exception {
  when(registry.preview(any())).thenAnswer(call->{
   var rows=new StudentRegistryWorkbook().read(call.getArgument(0));
   return new StudentRegistryService.Preview(rows.size(),0,0,rows.size(),0,java.util.List.of());
  });
  byte[] bytes=StudentRegistryExcelCompatibilityTest.linkedTemplate("mailto:synthetic@example.invalid","hyperlink").getBytes();
  var response=post(token(Role.ADMIN),bytes);
  assertEquals(200,response.statusCode());assertTrue(response.body().contains("\"total\":1"));
  verify(registry,never()).confirm(any(),any());verifyNoInteractions(users);
 }
 @Test void missingMalformedExpiredAndWrongRoleRemainDenied()throws Exception {
  assertEquals(401,post(null,new byte[]{1}).statusCode());
  var malformed=post("not-a-jwt",new byte[]{1});assertEquals(401,malformed.statusCode());assertTrue(malformed.body().contains("INVALID_OR_EXPIRED_TOKEN"));
  String expired=io.jsonwebtoken.Jwts.builder().subject("71").expiration(new java.util.Date(System.currentTimeMillis()-60000)).signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(new byte[32])).compact();
  assertEquals(401,post(expired,new byte[]{1}).statusCode());
  for(Role role:java.util.List.of(Role.STUDENT,Role.PROFESSOR,Role.SUPER_ADMIN))assertEquals(403,post(token(role),new byte[]{1}).statusCode());
  verifyNoInteractions(registry);
 }
 @Test void databaseFailureAndOversizedMultipartAreNotReportedAs401()throws Exception {
  when(registry.preview(any())).thenThrow(new org.springframework.jdbc.BadSqlGrammarException("synthetic","sensitive SQL must not appear",new java.sql.SQLException("private details")));
  var response=post(token(Role.ADMIN),new byte[]{1});assertEquals(503,response.statusCode());assertTrue(response.body().contains("REGISTRY_UNAVAILABLE"));assertFalse(response.body().contains("private details"));assertFalse(response.body().contains("sensitive SQL"));
  assertEquals(413,post(token(Role.ADMIN),new byte[3*1024*1024+1]).statusCode());
 }
}
