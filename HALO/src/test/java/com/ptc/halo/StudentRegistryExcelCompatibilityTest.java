package com.ptc.halo;
import com.ptc.halo.service.StudentRegistryWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class StudentRegistryExcelCompatibilityTest {
 @TempDir Path temp;
 @Test void excelEmailHyperlinksAreIgnoredButOtherExternalRelationshipsAreRejected()throws Exception {
  var parser=new StudentRegistryWorkbook();
  assertEquals(1,parser.read(linkedTemplate("mailto:synthetic@example.invalid","hyperlink")).size());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->parser.read(linkedTemplate("https://example.invalid/data","hyperlink")));
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->parser.read(linkedTemplate("mailto:synthetic@example.invalid","externalLink")));
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->parser.read(linkedTemplate("file:///private","hyperlink")));
 }
 static MockMultipartFile linkedTemplate(String target,String type)throws Exception {
  var out=new java.io.ByteArrayOutputStream();
  try(var zip=new java.util.zip.ZipOutputStream(out);var input=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(new StudentRegistryWorkbook().template()))) {
   for(java.util.zip.ZipEntry entry;(entry=input.getNextEntry())!=null;) {zip.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));input.transferTo(zip);zip.closeEntry();}
   zip.putNextEntry(new java.util.zip.ZipEntry("xl/worksheets/_rels/sheet1.xml.rels"));
   zip.write(("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/"+type+"\" TargetMode=\"External\" Target=\""+target+"\"/></Relationships>").getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
  }
  return new MockMultipartFile("file","linked.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",out.toByteArray());
 }
 @Test void excelSavedFormattingAndSharedStringsAreAccepted()throws Exception {
  byte[] bytes=getClass().getResourceAsStream("/registry/excel-formatted.xlsx").readAllBytes();
  var rows=new StudentRegistryWorkbook().read(new MockMultipartFile("file","excel-formatted.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes));
  assertEquals(1,rows.size());assertEquals("23AHRT-0168",rows.get(0).studentId());assertEquals("Synthetic Student",rows.get(0).fullName());
 }
 @Test void generatedHaloTemplateIsAReadableWorkbookFile()throws Exception {
  var parser=new StudentRegistryWorkbook();Path file=temp.resolve("halo-template.xlsx");Files.write(file,parser.template());
  assertEquals(1,parser.read(new MockMultipartFile("file",file.getFileName().toString(),"application/octet-stream",Files.readAllBytes(file))).size());
 }
}
