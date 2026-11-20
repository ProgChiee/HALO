package com.ptc.halo;

import com.ptc.halo.service.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ObtlTableBoundaryTest {
    static final Path ORIGINAL=Path.of(System.getProperty("cuisine.pdf",System.getProperty("user.home")+"/Downloads/OBTL_International Cuisine_V3_Dr Evardone_07222026 - Carlo Jay Evardone.pdf"));
    static ObtlSections.Detection original()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ORIGINAL),"Verified local Cuisine PDF not available; supply -Dcuisine.pdf");
        byte[] bytes=Files.readAllBytes(ORIGINAL);
        assertEquals("ba446f26560b64301414fe965c53466f990142bcbb759174294aa62b30fbb108",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        return ObtlPdfText.extract(bytes,"Cuisine").detection();
    }
    static ObtlSections.Section section(ObtlSections.Detection d,String prefix){return d.sections().stream().filter(s->s.heading().startsWith(prefix)).findFirst().orElseThrow();}
    static String text(ObtlSections.Detection d,String prefix){return ObtlSections.text(d,section(d,prefix)).replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
    @Test void originalFourRowsKeepEveryColumnAndExcludeNeighbours()throws Exception {
        var d=original();assertEquals(6,d.pages().size());
        String[] labels={"Chapter 1", "Chapter 2 & 3", "Chapter 4", "Chapter 5"};
        String[][] expected={
            {"historical influences","unique ingredients","identify distinctive","explain the historical","interactive lecture","culinary map","reflection paper"},
            {"historical development","compare greek","differentiate the culinary","comparative discussion","video documentary","comparative matrix"},
            {"italian culinary heritage","analyze regional","explain","lecture- demonstration","recipe analysis","practical cooking activity"},
            {"classical french","apply fundamental","demonstrate an understanding","guided laboratory","culinary video","practical laboratory"}};
        String[] unique={"unique ingredients","comparative matrix","italian culinary heritage","classical french"};
        for(int i=0;i<labels.length;i++){
            String value=text(d,labels[i]);
            for(String phrase:expected[i])assertTrue(value.contains(phrase),labels[i]+" missing "+phrase);
            for(int j=0;j<unique.length;j++)if(i!=j)assertFalse(value.contains(unique[j]),labels[i]+" leaked "+unique[j]);
            var s=section(d,labels[i]);assertEquals(List.of(3),s.spans().stream().map(ObtlSections.Span::page).toList());
            assertTrue(s.warnings().contains("PROFESSOR_BOUNDARY_REVIEW_REQUIRED"));assertFalse(s.warnings().contains("AMBIGUOUS_TABLE_BOUNDARY"));
        }
    }
    static void label(PDPageContentStream c,float x,float y,String value)throws IOException {
        c.beginText();c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),9);c.newLineAtOffset(x,y);c.showText(value);c.endText();
    }
    static void page(PDDocument doc,boolean continuation,boolean missing,boolean borderless)throws Exception{
        var p=new PDPage();doc.addPage(p);
        try(var c=new PDPageContentStream(doc,p)){
            if(!borderless){
                for(int x:new int[]{30,100,290,580}){c.moveTo(x,400);c.lineTo(x,730);}
                for(int y:new int[]{730,700,400}){c.moveTo(30,y);c.lineTo(580,y);}
                // Week cell spans both rows, while chapter/content cells have their own separator.
                if(!missing){c.moveTo(100,550);c.lineTo(580,550);}c.stroke();
            }
            label(c,35,715,"Week");label(c,105,715,"Content Standards");label(c,295,715,"Knowledge / activities");
            label(c,35,540,"Weeks 2-5");
            if(!continuation)label(c,105,600,"Chapter 2 & 3 Greek Cuisine");
            label(c,295,680,continuation?"Greek continuation assessment":"Greek complete outcomes");
            label(c,105,450,"Chapter 4 Italian Cuisine");label(c,295,530,"Italian culinary heritage");
            label(c,295,420,"Italian assessment task");
        }
    }
    static byte[] fixture(boolean continuation,boolean missing,boolean borderless)throws Exception{
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){
            if(continuation){
                var p=new PDPage();doc.addPage(p);try(var c=new PDPageContentStream(doc,p)){
                    for(int x:new int[]{30,100,290,580}){c.moveTo(x,400);c.lineTo(x,730);}
                    for(int y:new int[]{730,700,400}){c.moveTo(30,y);c.lineTo(580,y);}c.stroke();
                    label(c,105,715,"Content Standards");label(c,105,550,"Chapter 2 & 3 Greek Cuisine");label(c,295,680,"Greek first page");
                }
            }
            page(doc,continuation,missing,borderless);doc.save(out);return out.toByteArray();
        }
    }
    @Test void centeredHeadingsMergedWeeksAndSamePageRows()throws Exception{
        var d=ObtlPdfText.extract(fixture(false,false,false),"Cuisine").detection();
        assertTrue(text(d,"Chapter 2").contains("greek complete outcomes"));assertTrue(text(d,"Chapter 2").contains("2-5"));
        assertFalse(text(d,"Chapter 2").contains("italian"));assertTrue(text(d,"Chapter 4").contains("italian culinary heritage"));
        assertTrue(text(d,"Chapter 4").contains("italian assessment task"));assertTrue(text(d,"Chapter 4").contains("2-5"));
    }
    @Test void continuedRowIncludesNextPageWithoutNextChapter()throws Exception{
        var d=ObtlPdfText.extract(fixture(true,false,false),"Cuisine").detection();
        assertEquals(List.of(1,2),section(d,"Chapter 2").spans().stream().map(ObtlSections.Span::page).toList());
        assertTrue(text(d,"Chapter 2").contains("greek continuation assessment"));assertFalse(text(d,"Chapter 2").contains("italian"));
        assertTrue(section(d,"Chapter 2").warnings().contains("TABLE_CONTINUATION_REQUIRES_REVIEW"));
    }
    @Test void missingSeparatorCannotCreateConfirmableMixedSection()throws Exception{
        var d=ObtlPdfText.extract(fixture(false,true,false),"Cuisine").detection();
        assertTrue(d.pages().get(0).ambiguousTable());assertTrue(d.sections().stream().allMatch(s->s.spans().isEmpty()));
    }
    @Test void borderlessCurriculumTableRequiresReview()throws Exception{
        var d=ObtlPdfText.extract(fixture(false,false,true),"Cuisine").detection();
        assertTrue(d.pages().get(0).ambiguousTable());assertTrue(d.sections().stream().allMatch(s->s.spans().isEmpty()));
    }
    @Test void originalSurvivesBoundedWorkerAndJsonRoundTrip()throws Exception{
        original();
        var result=new ObtlExtractionProcess().extract(Files.readAllBytes(ORIGINAL));
        assertFalse(text(result.detection(),"Chapter 2").contains("italian culinary"));
        assertFalse(text(result.detection(),"Chapter 4").contains("classical french"));
        assertFalse(result.detection().pages().get(2).rows().isEmpty());
    }
    @Test void incompatibleContinuationGridCannotBeApproved(){
        var first=new ObtlSections.Page(1,"Chapter 1\nGreek\n",List.of(new ObtlSections.Row(0,16,"Chapter 1",false,0,0,100,50,"grid-a")),false);
        var next=new ObtlSections.Page(2,"Uncertain continuation",List.of(new ObtlSections.Row(0,22,"",true,0,0,100,50,"grid-b")),false);
        var d=ObtlSections.detect(List.of(first,next),"Greek");
        assertTrue(d.sections().get(0).warnings().contains("AMBIGUOUS_TABLE_BOUNDARY"));
        assertFalse(text(d,"Chapter 1").contains("uncertain"));
    }
    @Test void shadedNumericWeeksOpenPageEdgeAndMissingWeekArePreserved()throws Exception{
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){
            for(int p=0;p<2;p++){
                var page=new PDPage();doc.addPage(page);try(var c=new PDPageContentStream(doc,page)){
                    // Many shaded rectangles must not become extra table columns.
                    c.setNonStrokingColor(java.awt.Color.LIGHT_GRAY);
                    for(int i=0;i<25;i++){c.addRect(30+i*2,550,10,150);c.fill();}
                    c.setNonStrokingColor(java.awt.Color.BLACK);
                    for(int x:new int[]{30,100,290,580}){c.moveTo(x,400);c.lineTo(x,730);}
                    for(int y:new int[]{550,400}){c.moveTo(30,y);c.lineTo(580,y);}
                    if(p==0){for(int y:new int[]{730,700}){c.moveTo(30,y);c.lineTo(580,y);}}
                    c.stroke();
                    if(p==0){label(c,35,715,"Week");label(c,105,715,"Content Standards");label(c,295,715,"Outcomes");}
                    label(c,35,600,p==0?"9":"12");label(c,105,600,p==0?"MIDTERM EXAMINATION":"Starch Cookery");label(c,295,680,p==0?"Exam-only task":"Starch-only task");
                    label(c,35,450,p==0?"10":"13");label(c,105,450,p==0?"Salads":"Meat Cookery");label(c,295,530,p==0?"Salad-only task":"Meat-only task");
                }
            }
            doc.save(out);var d=ObtlPdfText.extract(out.toByteArray(),"").detection();
            assertEquals(List.of("9","10","12","13"),d.sections().stream().map(s->s.heading().split(" ")[1]).toList());
            assertTrue(d.sections().get(0).warnings().contains("EXAMINATION_SECTION"));
            assertTrue(text(d,"Week 12").contains("starch-only task"));assertFalse(text(d,"Week 12").contains("meat-only task"));
        }
    }
    @Test void topicRowsNeedVerifiedGeometryEvenWithoutChapterOrWeekLabels()throws Exception{
        for(boolean ruled:List.of(true,false))try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){
            var page=new PDPage();doc.addPage(page);try(var c=new PDPageContentStream(doc,page)){
                if(ruled){
                    for(int x:new int[]{30,250,580}){c.moveTo(x,400);c.lineTo(x,730);}
                    for(int y:new int[]{730,700,550,400}){c.moveTo(30,y);c.lineTo(580,y);}c.stroke();
                }
                label(c,35,715,"Content Standards");label(c,255,715,"Learning Outcomes");
                label(c,35,600,"Kitchen Safety");label(c,255,680,"Safety-only outcomes");
                label(c,35,450,"Food Presentation");label(c,255,530,"Presentation-only outcomes");
            }
            doc.save(out);var d=ObtlPdfText.extract(out.toByteArray(),"").detection();
            if(ruled){assertEquals(2,d.sections().size());assertTrue(text(d,"Kitchen Safety").contains("safety-only"));assertFalse(text(d,"Kitchen Safety").contains("presentation"));}
            else{assertTrue(d.pages().get(0).ambiguousTable());assertTrue(d.sections().stream().allMatch(s->s.spans().isEmpty()));}
        }
    }
    @Test void examCoverageCannotDetermineTopicColumnEvenWhenItStartsWithWeeks()throws Exception{
        for(boolean knownHeader:List.of(true,false))try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){
            var p=new PDPage();doc.addPage(p);try(var c=new PDPageContentStream(doc,p)){
                for(int x:new int[]{30,90,580}){c.moveTo(x,400);c.lineTo(x,730);}
                c.moveTo(280,400);c.lineTo(280,700);
                for(int y:new int[]{730,700,550,400}){c.moveTo(30,y);c.lineTo(580,y);}c.stroke();
                label(c,35,715,knownHeader?"Week":"Schedule");label(c,100,715,knownHeader?"Essential Learning":"Learning");
                label(c,35,620,"9");label(c,100,620,"MIDTERM EXAMINATION");label(c,285,680,"Weeks 1-8 - coverage only");
                label(c,35,470,"10");label(c,100,470,"Nutrition and Recovery");label(c,285,530,"Nutrition-only outcomes");
            }
            doc.save(out);var d=ObtlPdfText.extract(out.toByteArray(),"").detection();
            if(knownHeader){
                assertEquals(2,d.sections().size());assertEquals("Week 9 — MIDTERM EXAMINATION",d.sections().get(0).heading());
                assertTrue(text(d,"Week 9").contains("weeks 1-8"));assertFalse(text(d,"Week 9").contains("nutrition"));
                assertFalse(text(d,"Week 10").contains("coverage only"));assertTrue(d.sections().get(0).warnings().contains("EXAMINATION_SECTION"));
            }else{assertTrue(d.pages().get(0).ambiguousTable());assertTrue(d.sections().stream().allMatch(s->s.spans().isEmpty()));}
        }
    }
}
