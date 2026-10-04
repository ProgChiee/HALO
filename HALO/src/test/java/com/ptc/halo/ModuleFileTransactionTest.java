package com.ptc.halo;

import com.ptc.halo.controller.AiLearningModuleController;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.open-in-view=false"},showSql=false)
@Import({AiLearningModuleController.class,ModuleFileTransactionTest.Config.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ModuleFileTransactionTest {
 static Path directory;
 @TestConfiguration static class Config {
  @Bean FileUploadService storage() throws Exception {directory=Files.createTempDirectory("halo-file-tx-");return new FileUploadService(new LessonStoragePaths(directory.toString()));}
 }
 @Autowired AiLearningModuleController controller;
 @org.springframework.boot.test.mock.mockito.SpyBean FileUploadService storage;
 @Autowired AiLearningModuleRepository modules;
 @Autowired AiLearningFileRepository files;
 @Autowired SubjectRepository subjects;
 @Autowired WeekRepository weeks;
 @Autowired ProfessorRepository professors;
 @Autowired UserRepository users;
 @MockBean AiGenerationService generation;
 @MockBean AssessmentService assessment;
 @MockBean ActivityLogService audit;
 @MockBean com.ptc.halo.component.ModuleMaterialIndex index;
 Authentication owner,other;
 Long weekId;
 @BeforeEach void setup() throws Exception {
  reset(audit,storage);files.deleteAll();modules.deleteAll();weeks.deleteAll();subjects.deleteAll();professors.deleteAll();users.deleteAll();
  try(var stream=Files.list(directory)){for(var file:stream.toList())Files.delete(file);}
  var u=new UserEntity();u.setName("Owner");u.setEmail("owner@example.test");u.setRole(Role.PROFESSOR);u.setStatus(Status.ACTIVE);u=users.saveAndFlush(u);
  owner=new UsernamePasswordAuthenticationToken(u.getEmail(),"unused");
  var p=new ProfessorEntity();p.setUser(u);p.setProfessorId("P");p=professors.saveAndFlush(p);
  var stranger=new UserEntity();stranger.setName("Other");stranger.setEmail("other@example.test");stranger.setRole(Role.PROFESSOR);stranger.setStatus(Status.ACTIVE);users.saveAndFlush(stranger);other=new UsernamePasswordAuthenticationToken(stranger.getEmail(),"unused");
  var s=new SubjectEntity();s.setSubjectCode("S");s.setSubjectName("Subject");s.setProfessor(p);s=subjects.saveAndFlush(s);
  var w=new WeekEntity();w.setSubject(s);w.setTitle("Week");w.setWeekNumber(1);weekId=weeks.saveAndFlush(w).getId();
 }
 @AfterAll static void cleanup() throws Exception {if(directory!=null){try(var stream=Files.list(directory)){for(var file:stream.toList())Files.delete(file);}Files.delete(directory);}}
 static byte[] validBytes(){
  try(var bytes=new java.io.ByteArrayOutputStream()) {
   javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);return bytes.toByteArray();
  }catch(java.io.IOException e){throw new IllegalStateException(e);}
 }
 MockMultipartFile upload(){return new MockMultipartFile("file","lesson.pdf","application/pdf",validBytes());}
 long physicalCount() throws Exception {try(var paths=Files.list(directory)){return paths.count();}}
 Long create(boolean attach) throws Exception {return controller.createModule(weekId,"lesson",null,null,attach?List.of(upload()):null,owner).getBody().getId();}
 void failAudit(){doThrow(new IllegalStateException("forced audit failure")).when(audit).createLog(any(),any(),anyString());}
 @Test void bothUploadRoutesPreserveHostileNamesOnlyAsMetadata() throws Exception {
  String[] names={"../../escape.pdf","C:\\temp\\lesson.pdf","..\\..\\escape.pdf","bad<>:\"|?*.pdf","CON.pdf"};
  List<org.springframework.web.multipart.MultipartFile> initial=java.util.Arrays.stream(names).map(name->(org.springframework.web.multipart.MultipartFile)new MockMultipartFile("file",name,"application/pdf",validBytes())).toList();
  var response=controller.createModule(weekId,"lesson",null,null,initial,owner).getBody();
  Long id=response.getId();
  assertEquals(names[0],response.getFiles().get(0).getOriginalFileName());
  for(int i=0;i<names.length;i++) {
   var result=controller.uploadFile(id,new MockMultipartFile("file",names[i],"application/pdf",validBytes()),owner).getBody();
   final String expected=names[i];
   assertTrue(result.getFiles().stream().anyMatch(f->expected.equals(f.getOriginalFileName())));
  }
  var records=files.findAll();assertEquals(names.length*2,records.size());
  assertEquals(java.util.Set.of(names),records.stream().map(AiLearningFileEntity::getOriginalFileName).collect(java.util.stream.Collectors.toSet()));
  for(var record:records) {
   assertEquals(java.util.UUID.fromString(record.getStoredFileName()).toString(),record.getStoredFileName());
   assertEquals(record.getStoredFileName(),record.getFilePath());
   Path physical=new LessonStoragePaths(directory.toString()).resolve(record.getFilePath());
   assertEquals(directory,physical.getParent());
   assertArrayEquals(validBytes(),Files.readAllBytes(physical));
   controller.deleteFile(record.getId(),owner);assertFalse(Files.exists(physical));
  }
  assertEquals(0,files.count());assertEquals(0,physicalCount());
 }
 @Test void invalidBatchAndLaterUploadHaveNoSideEffects() throws Exception {
  var bad=new MockMultipartFile("file","fake.pdf","application/pdf","not a pdf".getBytes());
  var error=assertThrows(ResponseStatusException.class,()->controller.createModule(weekId,"lesson",null,null,List.of(upload(),bad),owner));
  assertEquals(415,error.getStatusCode().value());assertEquals(0,physicalCount());assertEquals(0,files.count());assertEquals(0,modules.count());verifyNoInteractions(audit);
  Long id=create(false);clearInvocations(audit);
  assertThrows(ResponseStatusException.class,()->controller.uploadFile(id,bad,owner));
  assertEquals(0,physicalCount());assertEquals(0,files.count());verifyNoInteractions(audit);
 }
 @Test void tenFileLimitAppliesToInitialBatchAndExistingModule() throws Exception {
  var eleven=java.util.stream.IntStream.range(0,11).mapToObj(i->(org.springframework.web.multipart.MultipartFile)upload()).toList();
  var error=assertThrows(ResponseStatusException.class,()->controller.createModule(weekId,"lesson",null,null,eleven,owner));
  assertEquals("MODULE_FILE_LIMIT_EXCEEDED",error.getReason());assertEquals(0,physicalCount());assertEquals(0,files.count());verifyNoInteractions(audit);
  Long id=controller.createModule(weekId,"lesson",null,null,eleven.subList(0,9),owner).getBody().getId();
  controller.uploadFile(id,upload(),owner);assertEquals(10,files.count());clearInvocations(audit);
  error=assertThrows(ResponseStatusException.class,()->controller.uploadFile(id,upload(),owner));
  assertEquals(409,error.getStatusCode().value());assertEquals("MODULE_FILE_LIMIT_EXCEEDED",error.getReason());
  assertEquals(10,files.count());assertEquals(10,physicalCount());verifyNoInteractions(audit);
 }
 @Test void concurrentUploadsCannotBothClaimTenthSlot() throws Exception {
  var nine=java.util.stream.IntStream.range(0,9).mapToObj(i->(org.springframework.web.multipart.MultipartFile)upload()).toList();
  Long id=controller.createModule(weekId,"lesson",null,null,nine,owner).getBody().getId();clearInvocations(audit);
  var barrier=new java.util.concurrent.CyclicBarrier(2);
  doAnswer(call->{var result=call.callRealMethod();barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);return result;}).when(storage).validate(any());
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  java.util.concurrent.Callable<Boolean> request=()->{
   try {controller.uploadFile(id,upload(),owner);return true;}
   catch(org.springframework.dao.OptimisticLockingFailureException expected){return false;}
  };
  try {
   var first=pool.submit(request);var second=pool.submit(request);
   assertNotEquals(first.get(20,java.util.concurrent.TimeUnit.SECONDS),second.get(20,java.util.concurrent.TimeUnit.SECONDS));
   assertEquals(10,files.count());assertEquals(10,physicalCount());
   verify(audit,times(1)).createLog(any(),any(),anyString());
  } finally {pool.shutdownNow();}
 }
 @Test void successfulCreateUploadAndDeleteCommitFilesAndRowsTogether() throws Exception {
  Long id=create(true);assertEquals(1,files.count());assertEquals(1,physicalCount());
  controller.uploadFile(id,upload(),owner);assertEquals(2,files.count());assertEquals(2,physicalCount());
  var file=files.findAll().get(0);Path path=directory.resolve(file.getFilePath());
  doAnswer(call->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());assertTrue(Files.exists(path));return null;}).when(audit).createLog(any(),any(),anyString());
  controller.deleteFile(file.getId(),owner);assertFalse(files.existsById(file.getId()));assertFalse(Files.exists(path));assertEquals(1,physicalCount());
 }
 @Test void failedCreateCleansEveryNewFile() throws Exception {
  failAudit();assertThrows(IllegalStateException.class,()->controller.createModule(weekId,"lesson",null,null,List.of(upload(),upload()),owner));
  assertEquals(0,modules.count());assertEquals(0,files.count());assertEquals(0,physicalCount());
 }
 @Test void failedUploadRollsBackFileRecordAndPhysicalWrite() throws Exception {
  Long id=create(false);failAudit();assertThrows(IllegalStateException.class,()->controller.uploadFile(id,upload(),owner));
  assertEquals(0,files.count());assertEquals(0,physicalCount());assertTrue(modules.existsById(id));
 }
 @Test void failedDeletePreservesOriginalFileAndDatabaseRecord() throws Exception {
  create(true);var file=files.findAll().get(0);Path path=directory.resolve(file.getFilePath());failAudit();
  assertThrows(IllegalStateException.class,()->controller.deleteFile(file.getId(),owner));
  assertTrue(files.existsById(file.getId()));assertArrayEquals(validBytes(),Files.readAllBytes(path));
 }
 @Test void unauthorizedMutationsDoNotTouchStorage() throws Exception {
  assertEquals(404,assertThrows(ResponseStatusException.class,()->controller.createModule(weekId,"lesson",null,null,List.of(upload()),other)).getStatusCode().value());assertEquals(0,physicalCount());
  Long id=create(true);var file=files.findAll().get(0);
  assertEquals(404,assertThrows(ResponseStatusException.class,()->controller.uploadFile(id,upload(),other)).getStatusCode().value());
  assertEquals(404,assertThrows(ResponseStatusException.class,()->controller.deleteFile(file.getId(),other)).getStatusCode().value());
  assertEquals(1,files.count());assertEquals(1,physicalCount());assertTrue(Files.exists(directory.resolve(file.getFilePath())));
 }
}

