package com.ptc.halo;
import com.ptc.halo.service.ProfilePhotoService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
class StudentPhotoTest {
 @TempDir Path directory;
 byte[] image(String format, int size) throws Exception { var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(size,size,BufferedImage.TYPE_INT_RGB),format,out);return out.toByteArray(); }
 @Test void savesAndReloadsWithoutCrossStudentLeakAndReplacesAtomically() throws Exception {
  var service=new ProfilePhotoService(directory.toString());assertNull(service.read(1));
  service.save(1,new MockMultipartFile("photo","../../bad.jpg","image/jpeg",image("jpg",20)));
  assertEquals(20,ImageIO.read(new java.io.ByteArrayInputStream(new ProfilePhotoService(directory.toString()).read(1))).getWidth());assertNull(service.read(2));
  service.save(1,new MockMultipartFile("photo","new.png","image/png",image("png",30)));
  assertEquals(30,ImageIO.read(new java.io.ByteArrayInputStream(service.read(1))).getWidth());
  try(var files=java.nio.file.Files.list(directory)){assertEquals(1,files.count());}
 }
 @Test void rejectsInvalidOversizedAndNonRasterDataWithoutReplacingPhoto() throws Exception {
  var service=new ProfilePhotoService(directory.toString());service.save(1,new MockMultipartFile("photo",image("png",10)));var original=service.read(1);
  for(byte[] bytes:new byte[][]{new byte[0],"<svg>bad</svg>".getBytes(),image("gif",10),image("png",1025),new byte[2*1024*1024+1]}) {
   assertThrows(ResponseStatusException.class,()->service.save(1,new MockMultipartFile("photo","x.png","image/png",bytes)));assertArrayEquals(original,service.read(1));
  }
 }
}
