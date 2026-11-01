package com.ptc.halo;

import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.dtoResponse.StudentSubjectResponse;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import java.nio.file.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.transaction.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties="spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",showSql=false)
@Import({SubjectCoverService.class,SubjectCoverStorage.class,ProfessorAcademicService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class SubjectCoverTest {
    static final Path directory;
    static {try{directory=Files.createTempDirectory("halo-cover-test-");}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("halo.subject-cover-directory",directory::toString);}
    @Autowired ProfessorAcademicService academic;
    @Autowired SubjectCoverService covers; @Autowired SubjectCoverStorage storage;
    @Autowired UserRepository users; @Autowired ProfessorRepository professors; @Autowired SubjectRepository subjects;
    @MockBean StudentEnrollmentService enrollment; @MockBean ActivityLogService audit;
    UserEntity owner,other; long id;
    @BeforeEach void setup(){owner=user(Role.PROFESSOR);other=user(Role.PROFESSOR);var professor=new ProfessorEntity();professor.setUser(owner);professor.setProfessorId("COVER-"+owner.getId());professor=professors.saveAndFlush(professor);var s=new SubjectEntity();s.setProfessor(professor);s.setSubjectCode("COVER-"+owner.getId());s.setSubjectName("Synthetic cover subject");s.setYearLevel(YearLevel.FIRST_YEAR);id=subjects.saveAndFlush(s).getId();}
    UserEntity user(Role role){var u=new UserEntity();u.setName("Synthetic cover user");u.setEmail(java.util.UUID.randomUUID()+"@example.test");u.setRole(role);u.setStatus(Status.ACTIVE);return users.saveAndFlush(u);}
    static MockMultipartFile image(String type,int rgb) throws IOException {var image=new BufferedImage(32,18,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(new java.awt.Color(rgb));g.fillRect(0,0,32,18);g.dispose();var bytes=new ByteArrayOutputStream();assertTrue(ImageIO.write(image,type,bytes));return new MockMultipartFile("image","../../unrelated.exe","application/octet-stream",bytes.toByteArray());}
    @Test void absentCoverDoesNotCreateAFile() throws Exception {assertNull(covers.professorCover(owner,id));assertFalse(Files.exists(directory.resolve(id+".png")));}
    @Test void uploadReplaceRemovePreservesSubjectAndIgnoresFilename() throws Exception {
        covers.change(owner,id,image("png",0x00ff00),null);var first=covers.professorCover(owner,id);assertNotNull(first);
        covers.change(owner,id,image("jpeg",0xff0000),null);assertFalse(java.util.Arrays.equals(first,covers.professorCover(owner,id)));
        assertEquals("Synthetic cover subject",subjects.findById(id).orElseThrow().getSubjectName());
        covers.change(owner,id,null,null);assertNull(covers.professorCover(owner,id));covers.change(owner,id,null,null);
        assertFalse(Files.exists(directory.resolve("unrelated.exe")));
    }
    @Test void malformedUnsupportedOversizedAndExcessiveDimensionsPreserveExistingCover() throws Exception {
        covers.change(owner,id,image("png",1),null);var original=storage.read(id);
        var huge=new BufferedImage(8193,1,BufferedImage.TYPE_INT_RGB);var bytes=new ByteArrayOutputStream();ImageIO.write(huge,"png",bytes);
        for(var bad:java.util.List.of(new MockMultipartFile("image","x.png","image/png","not an image".getBytes()),new MockMultipartFile("image","x.png","image/png",new byte[SubjectCoverStorage.MAX_BYTES+1]),new MockMultipartFile("image","x.png","image/png",bytes.toByteArray()),image("gif",1))){assertThrows(ResponseStatusException.class,()->covers.change(owner,id,bad,null));assertArrayEquals(original,storage.read(id));}
    }
    @Test void ownershipAndRoleAreCheckedBeforeStorageMutation() throws Exception {
        var upload=image("png",1);assertThrows(ResponseStatusException.class,()->covers.change(other,id,upload,null));assertThrows(ResponseStatusException.class,()->covers.professorCover(other,id));
        assertThrows(ResponseStatusException.class,()->covers.change(user(Role.STUDENT),id,upload,null));assertNull(storage.read(id));verifyNoInteractions(audit);
    }
    @Test void auditRollbackDoesNotInstallPreparedImageOrLeaveTemporaryFiles() throws Exception {
        covers.change(owner,id,image("png",1),null);var original=storage.read(id);
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit).createLog(eq(owner),eq(ActivityType.MODULE),anyString());
        assertThrows(IllegalStateException.class,()->covers.change(owner,id,image("png",2),null));assertArrayEquals(original,storage.read(id));
        try(var files=Files.list(directory)){assertTrue(files.noneMatch(p->p.getFileName().toString().endsWith(".tmp")));}
    }
    @Test void studentsUseExistingMembershipOrBrowseVisibilityOnly() throws Exception {
        var student=user(Role.STUDENT);var row=new StudentSubjectResponse();row.setSubjectId(id);
        when(enrollment.list(student,true)).thenReturn(java.util.List.of(row));assertNull(covers.studentCover(student,id));verify(enrollment,never()).list(student,false);
        when(enrollment.list(student,true)).thenReturn(java.util.List.of());when(enrollment.list(student,false)).thenReturn(java.util.List.of(row));assertNull(covers.studentCover(student,id));
        when(enrollment.list(student,false)).thenReturn(java.util.List.of());assertThrows(ResponseStatusException.class,()->covers.studentCover(student,id));verifyNoInteractions(audit);
    }
    @Test void webpIsDecodedAndReencodedAsPng() throws Exception {
        byte[] webp=java.util.Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        covers.change(owner,id,new MockMultipartFile("image","image.webp","image/webp",webp),null);
        assertNotNull(ImageIO.read(new ByteArrayInputStream(storage.read(id))));assertEquals(137,storage.read(id)[0]&255);
    }
    @Test void creatingSubjectNeedsNoCoverAndReturnsBackwardCompatibleResource() throws Exception {
        var request=new com.ptc.halo.dtoRequest.SubjectRequest();request.setSubjectCode("NEW-"+id);request.setSubjectName("New subject "+id);request.setYearLevel(YearLevel.FIRST_YEAR);
        var created=academic.createSubject(request,owner);assertEquals("subjects/"+created.getId()+"/cover",created.getCoverUrl());assertNull(storage.read(created.getId()));
    }
    @Test void deletingSubjectCleansCoverOnlyAfterSuccessfulCommit() throws Exception {
        covers.change(owner,id,image("png",1),null);var original=storage.read(id);
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit).createLog(eq(owner),eq(ActivityType.MODULE),contains("Deleted subject"));
        assertThrows(IllegalStateException.class,()->academic.deleteSubject(id,owner));assertArrayEquals(original,storage.read(id));assertTrue(subjects.existsById(id));
        reset(audit);academic.deleteSubject(id,owner);assertNull(storage.read(id));assertFalse(subjects.existsById(id));
    }
    @AfterAll static void cleanup() throws IOException {try(var files=Files.list(directory)){for(var p:files.toList())Files.deleteIfExists(p);}Files.deleteIfExists(directory);}
}
