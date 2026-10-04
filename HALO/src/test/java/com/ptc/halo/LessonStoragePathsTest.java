package com.ptc.halo;

import com.ptc.halo.service.*;
import com.ptc.halo.component.ModuleMaterialIndex;
import com.ptc.halo.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LessonStoragePathsTest {
 @TempDir Path directory;
 public static class PathProbe {
  public static void main(String[] args) throws Exception {
   System.out.print(Files.readString(new LessonStoragePaths(args[0]).resolve(args[1])));
  }
 }
 @Test void separateProcessesWithDifferentWorkingDirectoriesReadSameFile() throws Exception {
  Files.writeString(directory.resolve("lesson.pdf"),"same original");
  for(String name:new String[]{"intellij","terminal"}) {
   Path cwd=Files.createDirectory(directory.resolve(name));
   String javaExecutable=Path.of(System.getProperty("java.home"),"bin","java").toString();
   var process=new ProcessBuilder(javaExecutable,"-cp",System.getProperty("java.class.path"),
     PathProbe.class.getName(),directory.toString(),"uploads/lessons/lesson.pdf")
     .directory(cwd.toFile()).redirectErrorStream(true).start();
   try {
    assertTrue(process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS));
    assertEquals(0,process.exitValue());
    assertEquals("same original",new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
   } finally {process.destroyForcibly();}
  }
 }
 @Test void portableAndLegacyPathsIgnoreWorkingDirectory() throws Exception {
  var paths=new LessonStoragePaths(directory.toString());
  Path file=Files.writeString(directory.resolve("lesson.pdf"),"content");
  String previous=System.getProperty("user.dir");
  try {
   for(String cwd:new String[]{directory.resolve("one").toString(),directory.resolve("two").toString()}) {
    System.setProperty("user.dir",cwd);
    assertEquals(file,paths.resolve("lesson.pdf"));
    assertEquals(file,paths.resolve("uploads/lessons/lesson.pdf"));
    assertEquals(file,paths.resolve("uploads\\lessons\\lesson.pdf"));
    assertEquals(file,paths.resolve(file.toString()));
    assertEquals("lesson.pdf",paths.storedPath(file));
   }
  } finally {System.setProperty("user.dir",previous);}
 }
 @Test void rejectsOutsidePathsAndRelativeRoot() {
  var paths=new LessonStoragePaths(directory.toString());
  for(String value:new String[]{"../outside.pdf","uploads/lessons/../../outside.pdf",directory.getParent().resolve("outside.pdf").toString(),""})
   assertThrows(IllegalArgumentException.class,()->paths.resolve(value));
  assertThrows(IllegalArgumentException.class,()->new LessonStoragePaths("uploads/lessons"));
 }
 @Test void uploadGenerationReadIndexAndDeleteSharePortablePath() throws Exception {
  var paths=new LessonStoragePaths(directory.toString());var upload=new FileUploadService(paths);
  byte[] pdf;
  try(var doc=new org.apache.pdfbox.pdmodel.PDDocument();var bytes=new java.io.ByteArrayOutputStream()) {
   var page=new org.apache.pdfbox.pdmodel.PDPage();doc.addPage(page);
   try(var content=new org.apache.pdfbox.pdmodel.PDPageContentStream(doc,page)) {
    content.beginText();content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);
    content.newLineAtOffset(30,700);content.showText("Hospitality includes hotel front office operations and guest services.");content.endText();
   }
   doc.save(bytes);pdf=bytes.toByteArray();
  }
  TransactionSynchronizationManager.initSynchronization();TransactionSynchronizationManager.setActualTransactionActive(true);
  try {
   var module=new AiLearningModuleEntity();module.setId(12L);
   String original="../folder\\lesson<>:?*.pdf";
   var file=upload.uploadFile(new MockMultipartFile("file",original,"application/pdf",pdf),module);org.springframework.test.util.ReflectionTestUtils.setField(file,"id",3L);module.addFile(file);
   assertEquals(original,file.getOriginalFileName());
   assertEquals(java.util.UUID.fromString(file.getStoredFileName()).toString(),file.getStoredFileName());
   assertFalse(Path.of(file.getFilePath()).isAbsolute());assertArrayEquals(pdf,Files.readAllBytes(paths.resolve(file.getFilePath())));
   var chat=mock(ChatClient.class);var index=new ModuleMaterialIndex(chat,new ObjectMapper(),paths);
   assertFalse(index.getOrBuild(module).chunks().isEmpty());verifyNoInteractions(chat);
   var builder=mock(ChatClient.Builder.class);when(builder.build()).thenReturn(chat);
   var generation=new AiGenerationService(builder,mock(com.ptc.halo.repository.AiLearningModuleRepository.class),new ObjectMapper(),index,mock(ModuleGenerationState.class),paths);
   var read=AiGenerationService.class.getDeclaredMethod("loadMediaFiles",AiLearningModuleEntity.class);read.setAccessible(true);
   assertEquals(1,((java.util.List<?>)read.invoke(generation,module)).size());
   upload.deleteFile(file.getFilePath());assertTrue(Files.exists(paths.resolve(file.getFilePath())));
   for(var callback:TransactionSynchronizationManager.getSynchronizations())callback.afterCommit();
   assertFalse(Files.exists(paths.resolve(file.getFilePath())));
  } finally {TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.setActualTransactionActive(false);}
 }
}
