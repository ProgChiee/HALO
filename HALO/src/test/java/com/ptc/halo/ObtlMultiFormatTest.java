package com.ptc.halo;

import com.ptc.halo.service.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Original documents stay outside Git. Supply -Dobtl.fixtures for another local fixture directory. */
class ObtlMultiFormatTest {
    static final Path ROOT=Path.of(System.getProperty("obtl.fixtures",System.getProperty("user.home")+
        "/Desktop/OBTL AHRT/(AHRT SUBJECT) Upload OBTL for each subject assigned for 1st semester SY26-27(File responses)"));
    static byte[] fixture(String name,String checksum)throws Exception{
        Path file=ROOT.resolve(name);Assumptions.assumeTrue(Files.isRegularFile(file),"Original fixture unavailable; provide -Dobtl.fixtures");
        byte[] bytes=Files.readAllBytes(file);
        assertEquals(checksum,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        return bytes;
    }
    static byte[] kitchen()throws Exception{return fixture("AHRT-KITCHEN ESSENTIALS & BASIC FOOD PREPARATION_OBTL.pdf","c231c687cfdc5b540697bfd9d46da26c950548d2d1ba07c2186456c75c5fe4ae");}
    static byte[] art()throws Exception{return fixture("ART APPRECIATION OBTL 2026-2027.docx - She-Ann Teves.pdf","bf81d666c77fae10206feaea938843f98a8497cfd879b6a718c2e8f93de97580");}
    static byte[] pathfit()throws Exception{return fixture("ASSOCIATES OBTL PATHFIT 1 - SONEE JAMES C. LAMORENA.pdf","0ee4f11412c3cd7a2e8dadb9484ba16f1297fa5c811551c52ba22e54c636d4bd");}
    static ObtlSections.Section week(ObtlSections.Detection d,int n){return ObtlTableBoundaryTest.section(d,"Week "+n+" —");}
    static void isolated(ObtlSections.Detection d,int count){
        assertEquals(count,d.sections().size());Set<String> offsets=new HashSet<>();
        for(var s:d.sections()){
            assertFalse(s.spans().isEmpty());assertFalse(s.warnings().contains("AMBIGUOUS_TABLE_BOUNDARY"));
            assertFalse(s.warnings().contains("TABLE_CONTINUATION_UNCONFIRMED"));
            assertTrue(s.warnings().contains("PROFESSOR_BOUNDARY_REVIEW_REQUIRED"));
            String text=ObtlSections.text(d,s).toLowerCase(Locale.ROOT);
            assertFalse(text.contains("basic readings"));assertFalse(text.contains("course policies"));
            for(var p:s.spans()){
                assertTrue(p.start()<p.end());assertTrue(p.end()<=d.pages().get(p.page()-1).text().length());
                for(int i=p.start();i<p.end();i++)assertTrue(offsets.add(p.page()+":"+i),"Overlapping source ownership");
            }
        }
    }
    @Test void cuisineAllActualChaptersActivitiesAndExaminations()throws Exception{
        var d=ObtlTableBoundaryTest.original();isolated(d,18);
        var chapters=d.sections().stream().filter(s->s.heading().startsWith("Chapter")).toList();
        assertEquals(13,chapters.size());
        for(var current:chapters){String value=ObtlSections.text(d,current);
            for(var other:chapters)if(other!=current)assertFalse(value.contains(other.heading()),"Neighbour chapter leaked");
        }
        for(int n=10;n<=16;n++)assertEquals(List.of(4),ObtlTableBoundaryTest.section(d,"Chapter "+n+":").spans().stream().map(ObtlSections.Span::page).toList());
        assertTrue(week(d,9).warnings().contains("EXAMINATION_SECTION"));assertTrue(week(d,18).warnings().contains("EXAMINATION_SECTION"));
        assertTrue(week(d,16).warnings().contains("CURRICULUM_ACTIVITY_SECTION"));
        assertEquals(List.of(4,5),week(d,17).spans().stream().map(ObtlSections.Span::page).toList());
        String activity=ObtlSections.text(d,week(d,17)).replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
        assertTrue(activity.contains("international menu planning"));assertTrue(activity.contains("plating and presentation"));assertFalse(activity.contains("final examination"));
    }
    @Test void kitchenActualWeeksKeepSourceInconsistenciesAndContinuation()throws Exception{
        var d=ObtlPdfText.extract(kitchen(),"").detection();isolated(d,18);
        assertEquals(List.of(1,2,3,4,5,6,7,8,9,10,12,13,14,15,16,17,18,19),d.sections().stream().map(s->Integer.valueOf(s.heading().split(" ")[1])).toList());
        assertTrue(week(d,9).warnings().contains("EXAMINATION_SECTION"));assertTrue(week(d,19).warnings().contains("EXAMINATION_SECTION"));
        assertEquals(List.of(5,6),week(d,15).spans().stream().map(ObtlSections.Span::page).toList());
        String fish=ObtlSections.text(d,week(d,15)).replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
        assertTrue(fish.contains("filleting"));assertTrue(fish.contains("sourcing (msc"));assertFalse(fish.contains("vegetable & fruit cookery"));
        // The original itself pairs starch cookery with meat classification. Never silently fix it.
        String starch=ObtlSections.text(d,week(d,12)).replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
        assertTrue(starch.contains("starch cookery"));assertTrue(starch.contains("meat classification"));
        assertTrue(week(d,12).warnings().contains("VERIFY_COLUMN_ASSOCIATIONS_AGAINST_ORIGINAL"));
        for(int n:new int[]{1,2,3,4})assertEquals(3,week(d,n).spans().get(0).page());
        for(int n:new int[]{5,6,7,8})assertEquals(4,week(d,n).spans().get(0).page());
    }
    @Test void artActualRangesPreserveSplitRowsAndExcludePolicies()throws Exception{
        var d=ObtlPdfText.extract(art(),"").detection();isolated(d,7);
        assertEquals(List.of("1-4","1-4","5-8","9","10-13","14-17","18"),d.sections().stream().map(s->s.heading().split(" ")[1]).toList());
        assertEquals(List.of(2,3),d.sections().get(1).spans().stream().map(ObtlSections.Span::page).toList());
        assertEquals(List.of(3,4),d.sections().get(2).spans().stream().map(ObtlSections.Span::page).toList());
        assertEquals(List.of(4,5),d.sections().get(4).spans().stream().map(ObtlSections.Span::page).toList());
        assertEquals(List.of(5,6),d.sections().get(5).spans().stream().map(ObtlSections.Span::page).toList());
        assertTrue(week(d,9).warnings().contains("EXAMINATION_SECTION"));assertTrue(week(d,18).warnings().contains("EXAMINATION_SECTION"));
        assertFalse(ObtlSections.text(d,d.sections().get(2)).contains("PRELUDE"));
        assertFalse(ObtlSections.text(d,d.sections().get(4)).contains("EXPOSITIONS"));
        assertTrue(ObtlSections.text(d,d.sections().get(4)).contains("No music, no life!"));
    }
    @Test void originalsFitBoundedChildWorkerContract()throws Exception{
        assertEquals(18,new ObtlExtractionProcess().extract(kitchen()).detection().sections().size());
        assertEquals(7,new ObtlExtractionProcess().extract(art()).detection().sections().size());
        assertEquals(18,new ObtlExtractionProcess().extract(pathfit()).detection().sections().size());
    }
    @Test void pathfitCoverageIsOnlyWeekNineEvidenceNotAHeadingOrMultiPageScope()throws Exception{
        var d=ObtlPdfText.extract(pathfit(),"").detection();isolated(d,18);
        assertEquals(java.util.stream.IntStream.rangeClosed(1,18).boxed().toList(),d.sections().stream().map(s->Integer.valueOf(s.heading().split(" ")[1])).toList());
        assertTrue(week(d,9).heading().contains("MIDTERM EXAMINATION"));assertTrue(week(d,18).heading().contains("FINAL EXAMINATION"));
        assertTrue(week(d,9).warnings().contains("EXAMINATION_SECTION"));assertTrue(week(d,18).warnings().contains("EXAMINATION_SECTION"));
        assertEquals(List.of(4),week(d,9).spans().stream().map(ObtlSections.Span::page).toList());
        assertEquals(List.of(5),week(d,18).spans().stream().map(ObtlSections.Span::page).toList());
        assertEquals(List.of(2,3),week(d,3).spans().stream().map(ObtlSections.Span::page).toList());
        String exam=ObtlSections.text(d,week(d,9)).replaceAll("\\s+"," ");
        assertTrue(exam.contains("Weeks 1–8"));assertFalse(exam.contains("Nutrition, Hydration"));assertFalse(exam.contains("Course Policies"));
        for(var section:d.sections()){
            assertFalse(section.heading().startsWith("Weeks 1"));
            String value=ObtlSections.text(d,section).replaceAll("\\s+"," ");
            if(section!=week(d,9))assertFalse(value.contains("Midterm Written"));
            if(section!=week(d,18))assertFalse(value.contains("Final Written"));
        }
        assertTrue(week(d,10).heading().contains("Nutrition, Hydration, and Exercise Recovery"));
        assertTrue(week(d,17).heading().contains("Final Performance Preparation"));
        assertFalse(week(d,17).warnings().contains("EXAMINATION_SECTION"));
    }
}
