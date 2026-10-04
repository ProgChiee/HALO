package com.ptc.halo;

import com.ptc.halo.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class LessonUploadValidationTest {
 @TempDir Path directory;
 FileUploadService service() throws Exception {return new FileUploadService(new LessonStoragePaths(directory.toString()));}
 @Test void detectsSupportedBytesInsteadOfTrustingMimeOrExtension() throws Exception {
  var service=service();
  for(String format:new String[]{"png","jpeg"}) {
   var bytes=new java.io.ByteArrayOutputStream();
   javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),format,bytes);
   var result=service.validate(new MockMultipartFile("file","../original.exe","application/octet-stream",bytes.toByteArray()));
   assertEquals("image/"+format,result.mediaType());assertEquals("../original.exe",result.originalName());
  }
  try(var doc=new org.apache.pdfbox.pdmodel.PDDocument();var bytes=new java.io.ByteArrayOutputStream()) {
   doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());doc.save(bytes);
   assertEquals("application/pdf",service.validate(new MockMultipartFile("file","scan.pdf","image/png",bytes.toByteArray())).mediaType());
  }
  try(var paths=Files.list(directory)){assertEquals(0,paths.count());}
 }
 @Test void rejectsEmptyUnsupportedCorruptAndOversizedWithoutWriting() throws Exception {
  var service=service();
  byte[][] invalid={new byte[0],"executable content".getBytes(),"%PDF-1.7 broken".getBytes(),new byte[]{(byte)137,80,78,71,13,10,26,10},new byte[]{(byte)255,(byte)216,(byte)255},new byte[FileUploadService.MAX_BYTES+1]};
  int[] statuses={400,415,400,400,400,413};
  for(int i=0;i<invalid.length;i++) {
   var file=new MockMultipartFile("file","fake.pdf","application/pdf",invalid[i]);
   var error=assertThrows(ResponseStatusException.class,()->service.uploadFile(file));
   assertEquals(statuses[i],error.getStatusCode().value());
  }
  try(var paths=Files.list(directory)){assertEquals(0,paths.count());}
 }
}
