package com.ptc.halo;
import com.ptc.halo.service.*;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.dtoRequest.StudentRequest;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace=org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest(properties={"spring.datasource.url=jdbc:h2:mem:registryfixtures;MODE=MariaDB;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"},showSql=false)
@Import({StudentRegistryService.class,StudentRegistryWorkbook.class,PasswordResetThrottle.class,PasswordResetProtection.class,AuthService.class,StudentRegistryIntegrationTest.Config.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class StudentRegistryIntegrationTest {
 @TestConfiguration static class Config {@Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder(4);}}
 @MockBean EmailService mail;@MockBean ActivityLogService audit;@MockBean AuthAuditService authAudit;
 @MockBean LoginTokenIssuer issuer;@MockBean org.springframework.security.authentication.AuthenticationManager manager;@MockBean com.ptc.halo.security.JwtService jwt;
 @Autowired StudentRegistryService service;@Autowired StudentRegistryWorkbook workbook;@Autowired AuthService auth;
 @Autowired UserRepository users;@Autowired StudentProfileRepository profiles;@Autowired JdbcTemplate jdbc;
 UserEntity admin;String code;
 @BeforeEach void setup()throws Exception {
  String sql=Files.readString(Path.of("database/V20261115_01__student_registry.sql"));
  for(String statement:sql.split(";"))if(!statement.isBlank())jdbc.execute(statement.replace("CREATE TABLE ","CREATE TABLE IF NOT EXISTS ").replace("CREATE INDEX ","CREATE INDEX IF NOT EXISTS "));
  jdbc.update("delete from student_registry");jdbc.update("delete from student_registry_import");jdbc.update("delete from password_reset_rate_limits");profiles.deleteAll();users.deleteAll();
  admin=new UserEntity();admin.setName("Registry Admin");admin.setEmail("registry-admin@example.test");admin.setRole(Role.ADMIN);admin.setStatus(Status.ACTIVE);admin.setPassword("synthetic");admin=users.saveAndFlush(admin);
  reset(mail,audit);doAnswer(call->{code=call.getArgument(1);return null;}).when(mail).sendRegistrationCode(anyString(),anyString());
 }
 static MockMultipartFile file(byte[] bytes){return new MockMultipartFile("file","registry.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes);}
 byte[] rows(String... rows)throws Exception {
  byte[] source=workbook.template();try(var in=new ZipInputStream(new ByteArrayInputStream(source));var out=new ByteArrayOutputStream();var zip=new ZipOutputStream(out)){
   for(ZipEntry entry;(entry=in.getNextEntry())!=null;){byte[] bytes=in.readAllBytes();if(entry.getName().equals("xl/worksheets/sheet1.xml")){
    String sheet=new String(bytes,StandardCharsets.UTF_8);sheet=sheet.substring(0,sheet.indexOf("<row r=\"2\""));int number=2;
    for(String row:rows){sheet+="<row r=\""+number+"\">";String[] values=row.split("\\|",-1);for(int c=0;c<4;c++)sheet+="<c r=\""+(char)('A'+c)+number+"\" t=\"inlineStr\"><is><t>"+values[c]+"</t></is></c>";sheet+="</row>";number++;}bytes=(sheet+"</sheetData></worksheet>").getBytes(StandardCharsets.UTF_8);
   }zip.putNextEntry(new ZipEntry(entry.getName()));zip.write(bytes);zip.closeEntry();}zip.finish();return out.toByteArray();}
 }
 String eligible(){return "23AHRT-0168|Juan Dela Cruz|juan@paterostechnologicalcollege.edu.ph|AHRT";}
 StudentRequest request(){var r=new StudentRequest();r.setStudentId("23AHRT-0168");r.setName("  juan   dela cruz ");r.setEmail(" JUAN@PATEROSTECHNOLOGICALCOLLEGE.EDU.PH ");r.setSection("AHRT 1A");r.setYearLevel(YearLevel.FIRST_YEAR);r.setPassword("test-password");r.setVerificationCode(code);return r;}
 void imported()throws Exception{service.confirm(file(rows(eligible())),admin);}
 void issued(){service.requestCode("23AHRT-0168","juan@paterostechnologicalcollege.edu.ph","127.0.0.1");}
 @Test void previewIsReadOnlyAndConfirmImportsOnlyEligible()throws Exception{
  var input=file(rows(eligible(),"23BSIT-0001|Other Student|other@paterostechnologicalcollege.edu.ph|BSIT","bad|Missing|bad|AHRT"));
  var p=service.preview(input);assertEquals(3,p.total());assertEquals(1,p.eligible());assertEquals(1,p.ineligible());assertEquals(1,p.invalid());assertEquals(0,jdbc.queryForObject("select count(*) from student_registry",Integer.class));
  service.confirm(input,admin);assertEquals(1,service.list("juan","ALL","UNREGISTERED",0,20).totalElements());assertEquals(1,service.history().size());assertEquals(1,users.count());assertEquals(1,service.preview(file(rows(eligible()))).duplicates());
 }
 @Test void duplicateRowsAndUnsafeFilesRejected()throws Exception{
  assertEquals(2,service.preview(file(rows(eligible(),eligible()))).duplicates());
  assertEquals(2,service.preview(file(rows(eligible(),eligible().replace("23AHRT-0168","24AHRT-0001")))).duplicates());
  for(byte[] bytes:List.of(new byte[0],new byte[]{1,2,3},new byte[2097153]))assertThrows(RuntimeException.class,()->workbook.read(file(bytes)));
  assertThrows(RuntimeException.class,()->workbook.read(new MockMultipartFile("file","bad.xls","",workbook.template())));
  assertEquals(1,service.preview(file(rows("23AHRT-0168||juan@paterostechnologicalcollege.edu.ph|AHRT"))).invalid());
  assertThrows(RuntimeException.class,()->workbook.read(file(rows())));
 }
 byte[] replaceXml(byte[] source,String from,String to)throws Exception {
  try(var in=new ZipInputStream(new ByteArrayInputStream(source));var out=new ByteArrayOutputStream();var zip=new ZipOutputStream(out)){
   for(ZipEntry entry;(entry=in.getNextEntry())!=null;){byte[] bytes=in.readAllBytes();zip.putNextEntry(new ZipEntry(entry.getName()));zip.write(new String(bytes,StandardCharsets.UTF_8).replace(from,to).getBytes(StandardCharsets.UTF_8));zip.closeEntry();}zip.finish();return out.toByteArray();}
 }
 @Test void rejectsHeadersFormulasExternalEntitiesAndExcessRows()throws Exception {
  byte[] valid=rows(eligible());
  for(String[] change:List.of(new String[]{"Student_ID","Wrong_Header"},new String[]{"<is><t>Juan","<f>1+1</f><is><t>Juan"},new String[]{"<worksheet ","<!DOCTYPE worksheet [<!ENTITY x SYSTEM 'file:///not-read'>]><worksheet "},new String[]{"sheet.main+xml","sheet.macroEnabled.main+xml"}))
   assertThrows(RuntimeException.class,()->workbook.read(file(replaceXml(valid,change[0],change[1]))));
  String[] tooMany=new String[1001];Arrays.fill(tooMany,eligible());assertThrows(RuntimeException.class,()->workbook.read(file(rows(tooMany))));
 }
 @Test void unknownAndIneligibleNeverReceiveCodesAndThrottleSurvivesFailures()throws Exception {
  service.requestCode("23AHRT-9999","missing@paterostechnologicalcollege.edu.ph","127.0.0.1");verifyNoInteractions(mail);
  imported();jdbc.update("update student_registry set eligibility_status='INELIGIBLE'");issued();verifyNoInteractions(mail);
  for(int i=0;i<protectionLimit();i++)service.admit("attempt@school.test","127.0.0.2",true);
  assertThrows(PasswordResetException.class,()->service.admit("attempt@school.test","127.0.0.2",true));assertEquals(1,users.count());
 }
 int protectionLimit(){return 10;}
 @Test void realExcelAndFilledHaloTemplatePreviewOverHttpWithoutWrites()throws Exception {
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new com.ptc.halo.controller.StudentRegistryController(service,workbook,users)).build();
  for(String fixture:List.of("excel-formatted.xlsx","halo-template-excel-filled.xlsx")){
   byte[] bytes=getClass().getResourceAsStream("/registry/"+fixture).readAllBytes();
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/admin/student-registry/preview").file(file(bytes)))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.total").value(1))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.eligible").value(1))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.invalid").value(0));
  }
  assertEquals(0,jdbc.queryForObject("select count(*) from student_registry",Integer.class));assertEquals(0,service.history().size());assertEquals(1,users.count());verifyNoInteractions(audit);
 }
 @Test void oversizedFieldsAndNumericStudentIdsAreRowErrors()throws Exception {
  var longField=file(rows("23AHRT-0168|"+"A".repeat(300)+"|juan@paterostechnologicalcollege.edu.ph|AHRT"));
  assertEquals(1,service.preview(longField).invalid());
  byte[] numeric=replaceXml(rows("123|Synthetic Student|juan@paterostechnologicalcollege.edu.ph|AHRT"),"<c r=\"A2\" t=\"inlineStr\"><is><t>123</t></is></c>","<c r=\"A2\" t=\"n\"><v>123</v></c>");
  assertEquals(1,service.preview(file(numeric)).invalid());
  byte[] extra=replaceXml(rows(eligible()),"</row></sheetData>","<c r=\"E2\" t=\"inlineStr\"><is><t>unexpected data</t></is></c></row></sheetData>");
  assertThrows(RuntimeException.class,()->workbook.read(file(extra)));
 }
 @Test void verifiedRegistrationClaimsOnceAndUsesOfficialName()throws Exception{
  imported();issued();assertNotNull(code);assertEquals(1,users.count());
  var r=request();var result=auth.registerStudent(r);assertEquals("Juan Dela Cruz",result.getName());
  assertEquals(2,users.count());assertEquals(1,service.list("","ALL","REGISTERED",0,20).totalElements());
  assertThrows(RegistrationValidationException.class,()->auth.registerStudent(request()));
  var saved=users.findByEmail("juan@paterostechnologicalcollege.edu.ph").orElseThrow();assertEquals(Status.ACTIVE,saved.getStatus());assertFalse(saved.getHasSuccessfulLogin());
 }
 @Test void invalidMismatchExpiredAndUnverifiedCannotCreateAccounts()throws Exception{
  imported();var unverified=request();assertThrows(RegistrationValidationException.class,()->auth.registerStudent(unverified));issued();
  var r=request();r.setEmail("other@paterostechnologicalcollege.edu.ph");StudentRequest mismatch=r;assertThrows(RegistrationValidationException.class,()->auth.registerStudent(mismatch));
  jdbc.update("update student_registry set verification_expires_at=TIMESTAMP '2000-01-01 00:00:00'");assertThrows(RegistrationValidationException.class,()->auth.registerStudent(request()));
  jdbc.update("delete from password_reset_rate_limits");issued();auth.registerStudent(request());assertEquals(2,users.count());
 }
 @Test void simultaneousClaimsCreateOneAccount()throws Exception{
  imported();issued();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try{Callable<Boolean> action=()->{start.await();try{auth.registerStudent(request());return true;}catch(RegistrationValidationException e){return false;}};
   var a=pool.submit(action);var b=pool.submit(action);start.countDown();assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));assertEquals(2,users.count());assertEquals(1,profiles.count());
  }finally{pool.shutdownNow();}
 }
 @Test void codeRequestAndWrongCodeNeverCreateOrClaimAnAccount()throws Exception {
  imported();issued();assertNotNull(code);
  assertEquals(1,users.count());assertEquals(0,profiles.count());
  var wrong=request();wrong.setVerificationCode(code.equals("000000")?"000001":"000000");
  assertThrows(RegistrationValidationException.class,()->auth.registerStudent(wrong));
  assertEquals(1,service.list("","ALL","UNREGISTERED",0,20).totalElements());
  assertEquals(1,users.count());verifyNoInteractions(issuer);
  auth.registerStudent(request());
  assertEquals(0,jdbc.queryForObject("select count(*) from student_registry where verification_hash is not null or verification_expires_at is not null",Integer.class));
 }
 @Test void failedDeliveryCanBeRetriedWithoutReservingAnAccount()throws Exception {
  imported();doThrow(new IllegalStateException("synthetic delivery failure")).when(mail).sendRegistrationCode(anyString(),anyString());
  issued();assertEquals(1,users.count());assertEquals(1,service.list("","ALL","UNREGISTERED",0,20).totalElements());
  // Only synthetic rate-limit fixtures are reset to simulate a later permitted retry.
  jdbc.update("delete from password_reset_rate_limits");reset(mail);
  doAnswer(call->{code=call.getArgument(1);return null;}).when(mail).sendRegistrationCode(anyString(),anyString());
  issued();auth.registerStudent(request());assertEquals(2,users.count());
 }
 @Test void accountCreationRollbackLeavesVerificationUsable()throws Exception {
  imported();issued();
  // Use the existing JPA transaction manager so account and registry writes roll back together.
  var transaction=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
  transaction.executeWithoutResult(status->{auth.registerStudent(request());status.setRollbackOnly();});
  assertEquals(1,users.count());assertEquals(0,profiles.count());
  assertEquals(1,service.list("","ALL","UNREGISTERED",0,20).totalElements());
  auth.registerStudent(request());assertEquals(2,users.count());
 }
 @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
 @Autowired PasswordResetThrottle sharedThrottle;
 @Test void registrationAndRecoveryRateCountersAreIndependent() {
  String address="synthetic@paterostechnologicalcollege.edu.ph",ip="127.0.0.11";
  service.admit(address,ip,false);
  assertEquals(429,assertThrows(PasswordResetException.class,()->service.admit(address,ip,false)).getStatus());
  assertDoesNotThrow(()->sharedThrottle.admit(address,ip,false));
  assertEquals(429,assertThrows(PasswordResetException.class,()->sharedThrottle.admit(address,ip,false)).getStatus());
  String busyIp="127.0.0.12";
  for(int i=0;i<20;i++)service.admit("synthetic"+i+"@example.test",busyIp,false);
  assertEquals(429,assertThrows(PasswordResetException.class,()->service.admit("next@example.test",busyIp,false)).getStatus());
  assertDoesNotThrow(()->sharedThrottle.admit("next@example.test",busyIp,false));
  assertEquals(1,users.count());assertEquals(0,profiles.count());
 }
 @Test void registeredCodeRetryReportsThrottleButNeverChangesRegisteredAccount()throws Exception {
  imported();issued();auth.registerStudent(request());
  var beforeRegistry=jdbc.queryForMap("select * from student_registry");
  var beforeUser=jdbc.queryForMap("select * from user_entity where email=?","juan@paterostechnologicalcollege.edu.ph");
  var controller=new com.ptc.halo.controller.AuthController(auth,mock(PasswordResetService.class),users);
  org.springframework.test.util.ReflectionTestUtils.setField(controller,"registry",service);
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
   .setControllerAdvice(new com.ptc.halo.controller.PasswordChangeValidationHandler()).build();
  var retry=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/register/student/code")
   .contentType("application/json").content("{\"studentId\":\"23AHRT-0168\",\"email\":\"juan@paterostechnologicalcollege.edu.ph\"}");
  mvc.perform(retry).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isTooManyRequests())
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("REGISTRATION_RATE_LIMITED"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value("Too many registration attempts. Please wait before trying again."))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("Retry-After"));
  verify(mail,times(1)).sendRegistrationCode(anyString(),anyString());
  assertEquals(beforeRegistry,jdbc.queryForMap("select * from student_registry"));
  assertEquals(beforeUser,jdbc.queryForMap("select * from user_entity where email=?","juan@paterostechnologicalcollege.edu.ph"));
  // Only this disposable test database: simulate a later admitted request without waiting.
  jdbc.update("delete from password_reset_rate_limits");
  mvc.perform(retry).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
  verify(mail,times(1)).sendRegistrationCode(anyString(),anyString());
  assertEquals(beforeRegistry,jdbc.queryForMap("select * from student_registry"));
  assertEquals(beforeUser,jdbc.queryForMap("select * from user_entity where email=?","juan@paterostechnologicalcollege.edu.ph"));
  assertEquals(2,users.count());assertEquals(1,profiles.count());
  assertThrows(RegistrationValidationException.class,()->auth.registerStudent(request()));
 }
 @Test void registeredStudentCannotGetAnotherCodeOrAccountAfterRealCooldown()throws Exception {
  imported();issued();auth.registerStudent(request());
  var beforeRegistry=jdbc.queryForMap("select * from student_registry");
  var beforeUser=jdbc.queryForMap("select * from user_entity where email=?","juan@paterostechnologicalcollege.edu.ph");
  var beforeProfile=jdbc.queryForMap("select * from student_profile_entity");
  var controller=new com.ptc.halo.controller.AuthController(auth,mock(PasswordResetService.class),users);
  org.springframework.test.util.ReflectionTestUtils.setField(controller,"registry",service);
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
   .setControllerAdvice(new com.ptc.halo.controller.PasswordChangeValidationHandler()).build();
  // Allow the actual default resend cooldown to expire. Do not clear counters or alter policy.
  Thread.sleep(61_000);
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/register/student/code")
   .contentType("application/json").content("{\"studentId\":\"23AHRT-0168\",\"email\":\"juan@paterostechnologicalcollege.edu.ph\"}"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value("If your details match an eligible registry record, a verification code will be emailed. Contact your school Admin if it does not arrive."));
  verify(mail,times(1)).sendRegistrationCode(anyString(),anyString());
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  for(boolean replayOriginalCode:List.of(true,false)) {
   var duplicate=request();if(!replayOriginalCode)duplicate.setVerificationCode(null);
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/register/student")
    .contentType("application/json").content(mapper.writeValueAsString(duplicate)))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("REGISTRATION_VALIDATION_FAILED"));
  }
  assertEquals(2,users.count());assertEquals(1,profiles.count());
  assertEquals(beforeRegistry,jdbc.queryForMap("select * from student_registry"));
  assertEquals(beforeUser,jdbc.queryForMap("select * from user_entity where email=?","juan@paterostechnologicalcollege.edu.ph"));
  assertEquals(beforeProfile,jdbc.queryForMap("select * from student_profile_entity"));
  verify(mail,times(1)).sendRegistrationCode(anyString(),anyString());
 }
 @Test void signupHttpFlowRequiresEmailedCodeBeforeCreatingStudent()throws Exception {
  imported();var controller=new com.ptc.halo.controller.AuthController(auth,mock(PasswordResetService.class),users);
  org.springframework.test.util.ReflectionTestUtils.setField(controller,"registry",service);
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
   .setControllerAdvice(new com.ptc.halo.controller.PasswordChangeValidationHandler()).build();
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/register/student")
   .contentType("application/json").content(mapper.writeValueAsString(request())))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
  assertEquals(1,users.count());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/register/student/code")
   .contentType("application/json").content("{\"studentId\":\"23AHRT-0168\",\"email\":\"juan@paterostechnologicalcollege.edu.ph\"}"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
  assertNotNull(code);assertEquals(1,users.count());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/register/student")
   .contentType("application/json").content(mapper.writeValueAsString(request())))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
  assertEquals(2,users.count());assertEquals(1,profiles.count());
  assertEquals(1,service.list("","ALL","REGISTERED",0,20).totalElements());
 }
 @Test void auditFailureRollsBackImportAndNoOtherRoleCanImport()throws Exception{
  var input=file(rows(eligible()));doThrow(new IllegalStateException("synthetic failure")).when(audit).createLog(any(),any(),anyString());assertThrows(IllegalStateException.class,()->service.confirm(input,admin));assertEquals(0,service.history().size());assertEquals(0,service.list("","ALL","ALL",0,20).totalElements());
  admin.setRole(Role.STUDENT);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.confirm(input,admin));
 }
}
