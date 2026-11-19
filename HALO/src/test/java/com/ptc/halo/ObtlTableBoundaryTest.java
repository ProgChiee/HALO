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
}
