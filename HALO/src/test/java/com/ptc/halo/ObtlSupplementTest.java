package com.ptc.halo;

import com.ptc.halo.service.*;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ObtlSupplementTest {
    static final Path PDF=Path.of(System.getProperty("supplement.pdf",System.getProperty("user.home")+"/Downloads/Chapter 1_ Exploring the Flavors and Food Culture of the Middle East.pdf"));
    static byte[] original()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(PDF),"Supply -Dsupplement.pdf for the private regression fixture");
        byte[] bytes=Files.readAllBytes(PDF);
        assertEquals("25117befd2a87979aad226b2c86e6437018fcaa77f416ad839b2d082919dc566",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        return bytes;
    }
    @Test void actualSupplementIncludesAllThreePagesWithoutInventingTableAmbiguity()throws Exception{
        var d=new ObtlExtractionProcess().extract(original()).detection();
        assertEquals(3,d.pages().size());assertEquals(1,d.sections().size());
        assertTrue(d.pages().stream().allMatch(p->p.rows().isEmpty()&&!p.ambiguousTable()));
        var section=d.sections().get(0);assertEquals(List.of(1,2,3),section.spans().stream().map(ObtlSections.Span::page).toList());
        for(var span:section.spans()){assertEquals(0,span.start());assertEquals(d.pages().get(span.page()-1).text().length(),span.end());}
        String text=ObtlSections.text(d,section).replaceAll("\\s+"," ");
        for(String expected:List.of("Learning Objectives","Cumin","Historical and Cultural Influences","Shawarma","Hummus","Falafel","Kabsa","Learning Activities","Conclusion"))assertTrue(text.contains(expected),expected);
        assertFalse(text.contains("Chapter 2"));assertTrue(section.warnings().contains("PROFESSOR_BOUNDARY_REVIEW_REQUIRED"));
    }
    static byte[] background(boolean stroke,boolean chapters)throws Exception {
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){
            var page=new PDPage();doc.addPage(page);
            try(var c=new PDPageContentStream(doc,page)){
                c.addRect(0,0,page.getMediaBox().getWidth(),page.getMediaBox().getHeight());
                if(stroke)c.stroke();else{c.setNonStrokingColor(java.awt.Color.WHITE);c.fill();}
                c.setNonStrokingColor(java.awt.Color.BLACK);
                ObtlTableBoundaryTest.label(c,50,700,"Chapter 1: Middle East");
                ObtlTableBoundaryTest.label(c,50,670,"Detailed instructional prose.");
                if(chapters){ObtlTableBoundaryTest.label(c,50,600,"Chapter 2: Greek Cuisine");ObtlTableBoundaryTest.label(c,50,570,"Different chapter evidence.");}
            }
            doc.save(out);return out.toByteArray();
        }
    }
    @Test void filledPageBackgroundIsNotATableButStrokedGeometryRemainsConservative()throws Exception{
        var plain=ObtlPdfText.extract(background(false,false),"Middle East").detection();
        assertFalse(plain.pages().get(0).ambiguousTable());assertFalse(plain.sections().get(0).spans().isEmpty());
        var stroked=ObtlPdfText.extract(background(true,false),"Middle East").detection();
        assertTrue(stroked.pages().get(0).ambiguousTable());assertTrue(stroked.sections().get(0).spans().isEmpty());
    }
    @Test void removingBackgroundDoesNotMergeMultipleChapters()throws Exception{
        var d=ObtlPdfText.extract(background(false,true),"Middle East").detection();
        assertEquals(2,d.sections().size());assertFalse(ObtlSections.text(d,d.sections().get(0)).contains("Greek Cuisine"));
    }
}
