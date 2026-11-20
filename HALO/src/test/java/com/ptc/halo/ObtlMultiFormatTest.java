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
    @Test void recoverClosedCateringRowsWithoutUsingExamCoverageAsSchedule() throws Exception {
        var bytes=fixture("AHRT-CATERING SERVICE MANAGEMENT_OBTL.pdf","3b07c819d174012bfe8375c3d13966f2404b25554c75d12528c857cac1311e5d");
        var d=ObtlPdfText.extract(bytes,"").detection();
        var rows=d.sections().stream().filter(s->s.warnings().contains(ObtlSections.MANUAL_REGION)).toList();
        assertEquals(List.of("Week 2","Week 3","Week 4","Week 5 & 6","Week 7 & 8","Week 9","Week 10 & 11","Week 12 & 13",
                "Week 14 & 15","Week 16","Week 17","Week 18"),rows.stream().map(s->s.heading().split(" — ")[0]).toList());
        assertEquals(2,rows.stream().filter(s->s.warnings().contains("EXAMINATION_SECTION")).count());
        for(int i=0;i<rows.size();i++){
            var span=rows.get(i).spans().get(0);
            assertEquals(3+i/4,span.page());
            var region=d.pages().get(span.page()-1).rows().get(i%4);
            assertEquals(region.start(),span.start());assertEquals(region.end(),span.end());
            if(i%4!=0){
                var previous=d.pages().get(span.page()-1).rows().get(i%4-1);
                assertTrue(previous.bottom()<=region.top());assertTrue(previous.end()<=span.start());
            }
        }
    }
    @Test void orphanedBreadTableDoesNotFallBackToFullDocument() throws Exception {
        var bytes=fixture("AHRT-BREAD & PASTRY PRODUCTION_OBTL.pdf","e254236f93f49b65cefb5f021b0cb1f7354f01742e683425563fe4ddc1190bb3");
        var d=ObtlPdfText.extract(bytes,"").detection();
        var safe=d.sections().stream().filter(s->!s.spans().isEmpty()).toList();
        var exam=safe.stream().filter(s->s.heading().startsWith("Week 9")).findFirst().orElseThrow();
        assertEquals(11,exam.spans().get(0).page());
        assertTrue(exam.warnings().contains("EXAMINATION_SECTION"));
        var first=ObtlTableBoundaryTest.section(d,"Week 2(1st)");
        var second=ObtlTableBoundaryTest.section(d,"Week 2(2nd)");
        assertTrue(first.warnings().contains(ObtlSections.MANUAL_REGION));
        assertEquals(4,first.spans().get(0).page());
        assertEquals(first.spans().get(0).end(),second.spans().get(0).start());
        String excerpt=ObtlSections.text(d,first).replaceAll("\\s+"," ");
        assertTrue(excerpt.contains("Proper care, use, storage"));
        assertFalse(excerpt.contains("Characteristics and functions of dry"));
        assertTrue(ObtlSections.text(d,second).replaceAll("\\s+"," ").contains("Characteristics and functions of dry"));
    }
    @Test void abttMergedScheduleHasIndependentChapterAndExaminationEvidence() throws Exception {
        var bytes=fixture("ABTT OBTL _Bulusan - Edna Bulusan.pdf","62028ae465a26712ef4b310f7138a631900b52ec8c87ed9f11e73a5d11a01195");
        var d=ObtlPdfText.extract(bytes,"").detection();
        var chapter=ObtlTableBoundaryTest.section(d,"Chapter 2");
        assertTrue(chapter.warnings().contains(ObtlSections.MANUAL_REGION));
        assertEquals(List.of(3),chapter.spans().stream().map(ObtlSections.Span::page).toList());
        var text=ObtlSections.text(d,chapter).replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
        for(String expected:List.of("tourism and its sectors","describe the characteristics","classify the different types"))
            assertTrue(text.contains(expected),expected);
        for(String other:List.of("what do managers","chapter 3","business tools","chapter test/quiz"))
            assertFalse(text.contains(other),other);
        assertEquals("recovery:merged|2-8",d.pages().get(2).rows().get(0).gridKey());
        assertTrue(d.pages().get(4).rows().stream().anyMatch(r->r.gridKey().equals("recovery:merged|10-17")&&r.heading().startsWith("Chapter 6")));
        assertTrue(ObtlTableBoundaryTest.section(d,"Chapter 6").warnings().contains("TABLE_CONTINUATION_UNCONFIRMED"));
        var eight=ObtlTableBoundaryTest.section(d,"Chapter 8");
        assertEquals(List.of(6),eight.spans().stream().map(ObtlSections.Span::page).toList());
        assertTrue(eight.warnings().contains(ObtlSections.MANUAL_REGION));
        assertFalse(eight.warnings().contains("TABLE_CONTINUATION_UNCONFIRMED"));
        String guest=ObtlSections.text(d,eight).replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
        assertTrue(guest.contains("customer and guest service activities"));
        assertFalse(guest.contains("procurement"));assertFalse(guest.contains("chapter 9"));
        assertEquals("recovery:merged|",d.pages().get(5).rows().get(0).gridKey());
        assertEquals(List.of(7),ObtlTableBoundaryTest.section(d,"Chapter 10").spans().stream().map(ObtlSections.Span::page).toList());
        for(String label:List.of("Week 9","Week 18"))
            assertTrue(ObtlTableBoundaryTest.section(d,label).warnings().contains("EXAMINATION_SECTION"));
    }
    @Test void unresolvedBarPagesStayBlocked() throws Exception {
        var d=ObtlPdfText.extract(fixture("AHRT-BAR & BEVERAGE MANAGEMENT_OBTL.pdf","4dc03b632cd555962eb3128b2880b6831179f1d0e7056eea857753ca731930a4"),"").detection();
        assertTrue(d.sections().stream().anyMatch(s->s.spans().isEmpty()));
        assertTrue(d.sections().stream().noneMatch(s->s.warnings().contains(ObtlSections.MANUAL_REGION)));
    }
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
