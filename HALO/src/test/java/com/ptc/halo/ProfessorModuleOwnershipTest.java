package com.ptc.halo;

import com.ptc.halo.controller.AiLearningModuleController;
import com.ptc.halo.dtoRequest.AiLearningModuleUpdateRequest;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.open-in-view=false"}, showSql=false)
@Import(AiLearningModuleController.class)
class ProfessorModuleOwnershipTest {
 @Autowired AiLearningModuleController controller;
 @Autowired AiLearningModuleRepository modules;
 @Autowired AiLearningFileRepository files;
 @Autowired SubjectRepository subjects;
 @Autowired WeekRepository weeks;
 @Autowired ProfessorRepository professors;
 @Autowired UserRepository users;
 @MockBean com.ptc.halo.component.ModuleMaterialIndex materialIndex;
 @MockBean FileUploadService storage;
 @MockBean AiGenerationService generation;
 @MockBean AssessmentService assessment;
 @MockBean ActivityLogService audit;
 @TempDir Path directory;
 Authentication owner, stranger;
 WeekEntity week;
 AiLearningModuleEntity module;
 AiLearningFileEntity file;
 Path original;
 @BeforeEach void setup() throws Exception {
  var a = user("owner"); var b = user("stranger");
  owner = new UsernamePasswordAuthenticationToken(a.getEmail(), "unused");
  stranger = new UsernamePasswordAuthenticationToken(b.getEmail(), "unused");
  var professor = new ProfessorEntity(); professor.setUser(a); professor.setProfessorId("owner"); professor=professors.saveAndFlush(professor);
  var subject = new SubjectEntity(); subject.setSubjectCode("OWN"); subject.setSubjectName("Owned"); subject.setProfessor(professor); subject=subjects.saveAndFlush(subject);
  week=new WeekEntity(); week.setSubject(subject); week.setWeekNumber(1); week.setTitle("Week"); week=weeks.saveAndFlush(week);
  module=new AiLearningModuleEntity(); module.setWeek(week); module.setStatus(LessonStatus.PENDING); module.setAiGenerationStatus(AiGenerationStatus.COMPLETED); module=modules.saveAndFlush(module);
  original=Files.writeString(directory.resolve("original.pdf"),"original bytes");
  file=new AiLearningFileEntity(); file.setOriginalFileName("original.pdf"); file.setStoredFileName("original.pdf"); file.setFilePath(original.toString()); file.setFileType("application/pdf"); module.addFile(file); file=files.saveAndFlush(file);
 }
 UserEntity user(String name) { var u=new UserEntity();u.setName(name);u.setEmail(name+"@example.test");u.setRole(Role.PROFESSOR);u.setStatus(Status.ACTIVE);return users.saveAndFlush(u); }
 interface Operation { void run() throws Exception; }
 void denied(Operation action) {
  var error=assertThrows(ResponseStatusException.class,action::run);
  assertEquals(404,error.getStatusCode().value()); assertEquals("RESOURCE_NOT_FOUND",error.getReason());
  assertEquals("RESOURCE_NOT_FOUND",controller.handleRequestError(error).getBody().get("code"));
 }
 MockMultipartFile upload() {return new MockMultipartFile("file","new.pdf","application/pdf",new byte[]{1,2,3});}
 void allDenied(Authentication actor) throws Exception {
  long moduleCount=modules.count(), fileCount=files.count();
  denied(()->controller.getModuleByWeek(week.getId(),actor));
  denied(()->controller.getModuleById(module.getId(),actor));
  denied(()->controller.createModule(week.getId(),"lesson",null,null,List.of(upload()),actor));
  denied(()->controller.updateModule(module.getId(),new AiLearningModuleUpdateRequest(),actor));
  denied(()->controller.generateLesson(module.getId(),actor));
  denied(()->controller.generateLesson(module.getId(),actor));
  denied(()->controller.approveLesson(module.getId(),actor));
  denied(()->controller.declineLesson(module.getId(),actor));
  denied(()->controller.uploadFile(module.getId(),upload(),actor));
  denied(()->controller.deleteFile(file.getId(),actor));
  assertEquals(moduleCount,modules.count()); assertEquals(fileCount,files.count());
  assertEquals("original bytes",Files.readString(original));
  assertEquals(LessonStatus.PENDING,modules.findById(module.getId()).orElseThrow().getStatus());
  verifyNoInteractions(storage,generation,assessment,audit,materialIndex);
 }
 @Test void crossProfessorCannotReadOrMutateAndFilesRemainIntact() throws Exception {allDenied(stranger);}
 @Test void unassignedSubjectCannotBeAccessedEvenByPreviousOwner() throws Exception {
  week.getSubject().setProfessor(null);subjects.saveAndFlush(week.getSubject());allDenied(owner);
 }
 @Test void ownerReadsUpdatesGeneratesDeclinesAndApproves() {
  assertEquals(module.getId(),controller.getModuleByWeek(week.getId(),owner).getBody().getId());
  assertEquals(module.getId(),controller.getModuleById(module.getId(),owner).getBody().getId());
  var edit=new AiLearningModuleUpdateRequest();edit.setLessonText("updated");controller.updateModule(module.getId(),edit,owner);
  assertEquals("updated",module.getLessonText());
  when(generation.generateLesson(module.getId())).thenAnswer(invocation->{module.setAiGenerationStatus(AiGenerationStatus.COMPLETED);return module;});
  controller.generateLesson(module.getId(),owner);controller.declineLesson(module.getId(),owner);
  assertEquals(LessonStatus.DECLINED,module.getStatus());
  controller.generateLesson(module.getId(),owner);controller.approveLesson(module.getId(),owner);
  assertEquals(LessonStatus.APPROVED,module.getStatus());verify(generation,times(2)).generateLesson(module.getId());verify(assessment).generateAssessment(module.getId());
 }
 @Test void ownerCreatesUploadsAndDeletes() throws Exception {
  var other=new WeekEntity();other.setSubject(week.getSubject());other.setWeekNumber(2);other.setTitle("Next");other=weeks.saveAndFlush(other);
  assertEquals(204,controller.getModuleByWeek(other.getId(),owner).getStatusCode().value());
  var created=controller.createModule(other.getId(),"lesson",null,null,null,owner).getBody();assertNotNull(created.getId());
  var added=new AiLearningFileEntity();added.setOriginalFileName("new.pdf");added.setStoredFileName("new.pdf");added.setFilePath(directory.resolve("new.pdf").toString());added.setFileType("application/pdf");
  when(storage.uploadFile(any(),eq(module))).thenReturn(added);
  controller.uploadFile(module.getId(),upload(),owner);assertNotNull(added.getId());
  controller.deleteFile(file.getId(),owner);verify(storage).deleteFile(original.toString());assertFalse(files.existsById(file.getId()));
 }

 @Test void filelessTextLinkAndEmptyModulesCannotPublish() {
  module.getFiles().clear();modules.saveAndFlush(module);
  for(String source:List.of("text","link","empty")) {
   module.setLessonText(source.equals("text")?"Lesson text":null);
   module.setYoutubeLink(source.equals("link")?"https://www.youtube.com/watch?v=example":null);
   var error=assertThrows(ResponseStatusException.class,()->controller.approveLesson(module.getId(),owner));
   assertEquals("MODULE_MATERIALS_REQUIRED",error.getReason());assertEquals(409,controller.handleRequestError(error).getStatusCode().value());
   assertEquals(LessonStatus.PENDING,module.getStatus());
  }
  verifyNoInteractions(materialIndex,assessment,audit);
 }
 @Test void realOriginalPdfIndexesBeforeSuccessfulPublication() throws Exception {
  try(var document=new org.apache.pdfbox.pdmodel.PDDocument()) {
   var page=new org.apache.pdfbox.pdmodel.PDPage();document.addPage(page);
   try(var stream=new org.apache.pdfbox.pdmodel.PDPageContentStream(document,page)) {
    stream.beginText();stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);
    stream.newLineAtOffset(50,700);stream.showText("Hospitality front office staff welcome guests and manage hotel reservations.");stream.endText();
   }
   document.save(original.toFile());
  }
  var index=new com.ptc.halo.component.ModuleMaterialIndex(mock(org.springframework.ai.chat.client.ChatClient.class),new com.fasterxml.jackson.databind.ObjectMapper(),new com.ptc.halo.service.LessonStoragePaths(directory.toString()));
  when(materialIndex.getOrBuild(any())).thenAnswer(call->index.getOrBuild(call.getArgument(0)));
  assertEquals(LessonStatus.APPROVED,controller.approveLesson(module.getId(),owner).getBody().getStatus());
  assertFalse(index.getOrBuild(module).chunks().isEmpty());
  var order=inOrder(materialIndex,assessment,audit);order.verify(materialIndex).getOrBuild(any());order.verify(assessment).generateAssessment(module.getId());order.verify(audit).createLog(any(),any(),anyString());
 }
 @Test void unreadableOriginalFilePreventsPublicationAndAssessment() {
  var index=new com.ptc.halo.component.ModuleMaterialIndex(mock(org.springframework.ai.chat.client.ChatClient.class),new com.fasterxml.jackson.databind.ObjectMapper(),new com.ptc.halo.service.LessonStoragePaths(directory.toString()));
  when(materialIndex.getOrBuild(any())).thenAnswer(call->index.getOrBuild(call.getArgument(0)));
  var error=assertThrows(ResponseStatusException.class,()->controller.approveLesson(module.getId(),owner));
  assertEquals("MODULE_MATERIAL_UNREADABLE",error.getReason());assertEquals(409,controller.handleRequestError(error).getStatusCode().value());
  assertEquals(LessonStatus.PENDING,module.getStatus());verifyNoInteractions(assessment,audit);
 }
}
