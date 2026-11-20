package com.ptc.halo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class ObtlStructureRecognizerTest {
    private final ObjectMapper json=new ObjectMapper();
    private ObtlStructureRecognizer recognizer;
    @AfterEach void close(){if(recognizer!=null)recognizer.close();}
    private List<ObtlStructureRecognizer.Suggestion> call(LessonSourceScope.Document d,Function<String,String> mock){
        recognizer=new ObtlStructureRecognizer(json,mock,1000);
        return recognizer.suggest(d);
    }
    private LessonSourceScope.Document synthetic(int n){
        var text=new StringBuilder();
        for(int i=1;i<=n;i++)text.append("Chapter ").append(i).append(" Topic\nDetails\n");
        return new LessonSourceScope.Document(1L,"fixture","fixture",ObtlSections.detect(List.of(new ObtlSections.Page(1,text.toString())),""));
    }
    private List<String> ids(String request){
        try{var result=new ArrayList<String>();for(var item:json.readTree(request))result.add(item.get("id").asText());return result;}
        catch(Exception e){throw new AssertionError(e);}
    }
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new AssertionError(e);}}
    @Test void barPdfReturnsReviewPointersOnly() throws Exception {
        var dir=Path.of(System.getProperty("obtl.fixtures",System.getProperty("user.home")+
            "/Desktop/OBTL AHRT/(AHRT SUBJECT) Upload OBTL for each subject assigned for 1st semester SY26-27(File responses)"));
        var file=dir.resolve("AHRT-BAR & BEVERAGE MANAGEMENT_OBTL.pdf");
        Assumptions.assumeTrue(Files.isRegularFile(file));
        byte[] bytes=Files.readAllBytes(file);
        var checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertEquals("4dc03b632cd555962eb3128b2880b6831179f1d0e7056eea857753ca731930a4",checksum);
        var detection=ObtlPdfText.extract(bytes,"").detection();
        var result=call(new LessonSourceScope.Document(1L,"fixture",checksum,detection),
            request->encode(ids(request).stream().map(List::of).toList()));
        assertEquals(Set.of(4,5,17),result.stream().flatMap(s->s.pages().stream()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(result.stream().allMatch(s->s.sectionId()==null));
    }
    @Test void knownSourceSuggestionUsesOriginalHeading(){
        var d=synthetic(2);
        var result=call(d,request->encode(List.of(List.of(ids(request).get(1)))));
        assertEquals(d.extraction().sections().get(1).heading(),result.get(0).heading());
    }
    @Test void inventedIdsRejected(){
        assertEquals("OBTL_RECOGNITION_UNVERIFIED",assertThrows(ResponseStatusException.class,
            ()->call(synthetic(2),request->"[[\"invented\"]]")).getReason());
    }
    @Test void differentOwnersCannotBeGrouped(){
        assertEquals("OBTL_RECOGNITION_UNVERIFIED",assertThrows(ResponseStatusException.class,
            ()->call(synthetic(2),request->encode(List.of(ids(request))))).getReason());
    }
    @Test void partialContinuationRejected(){
        var d=new LessonSourceScope.Document(1L,"fixture","checksum",ObtlSections.detect(List.of(
            new ObtlSections.Page(1,"Chapter 1 Topic"),new ObtlSections.Page(2,"continued text")),""));
        assertEquals("OBTL_RECOGNITION_UNVERIFIED",assertThrows(ResponseStatusException.class,
            ()->call(d,request->encode(List.of(List.of(ids(request).get(0)))))).getReason());
    }
    @Test void deadlineStopsWait(){
        recognizer=new ObtlStructureRecognizer(json,request->{try{Thread.sleep(30000);}catch(InterruptedException e){Thread.currentThread().interrupt();}return "[]";},20);
        assertEquals("OBTL_RECOGNITION_UNAVAILABLE",assertThrows(ResponseStatusException.class,()->recognizer.suggest(synthetic(1))).getReason());
    }
    @Test void excessiveCatalogMakesNoProviderCall(){
        var calls=new AtomicInteger();
        assertEquals("OBTL_RECOGNITION_BUDGET",assertThrows(ResponseStatusException.class,
            ()->call(synthetic(65),request->{calls.incrementAndGet();return "[]";})).getReason());
        assertEquals(0,calls.get());
    }
}
