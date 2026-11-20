package com.ptc.halo;
import com.ptc.halo.service.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class ObtlPdfTextTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path coldFonts;
    @Test void coldFontCacheCannotContaminateWorkerJson()throws Exception{
        var worker=new ObtlExtractionProcess(){@Override protected Process start(ProcessBuilder builder)throws IOException{
            builder.command().add(1,"-Duser.home="+coldFonts);
            builder.command().add(1,"-Dpdfbox.fontcache="+coldFonts);
            return super.start(builder);
        }};
        assertEquals(11,worker.extract(pdf(false,false)).detection().sections().size());
    }
    private byte[] pdf(boolean encrypted,boolean active)throws Exception{
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){
            for(int i=1;i<=11;i++){
                var page=new PDPage();doc.addPage(page);
                try(var text=new PDPageContentStream(doc,page)){
                    text.beginText();text.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
                    text.newLineAtOffset(40,700);text.showText("Chapter "+i+" Management");
                    text.newLineAtOffset(0,-20);text.showText("What is management? Discuss outcomes.");text.endText();
                }
            }
            if(encrypted)doc.protect(new StandardProtectionPolicy("owner","",new AccessPermission()));
            if(active)doc.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert('fixture')"));
            doc.save(out);return out.toByteArray();
        }
    }
    @Test void syntheticElevenChapterPdfPreservesPagesAndIsolation()throws Exception{
        var result=ObtlPdfText.extract(pdf(false,false),"Management");
        assertEquals(64,result.checksum().length());assertEquals(11,result.detection().sections().size());
        var first=result.detection().sections().get(0);
        assertEquals(1,first.spans().get(0).page());
        assertFalse(ObtlSections.text(result.detection(),first).contains("Chapter 2"));
    }
    @Test void boundedWorkerExtractsRealPdfAndRecoversAfterRejectedInput()throws Exception{
        var worker=new ObtlExtractionProcess();
        assertThrows(Exception.class,()->worker.extract("%PDF-invalid".getBytes()));
        var result=worker.extract(pdf(false,false));
        assertEquals(11,result.detection().sections().size());
        assertFalse(ObtlSections.text(result.detection(),result.detection().sections().get(0)).contains("Chapter 2"));
    }
    @Test void encryptedPdfIsRejectedEvenWhenEmptyPasswordOpensIt()throws Exception{
        assertThrows(IOException.class,()->ObtlPdfText.extract(pdf(true,false),""));
    }
    @Test void activeActionsAreRejected()throws Exception{
        assertThrows(IOException.class,()->ObtlPdfText.extract(pdf(false,true),""));
    }
    @Test void corruptAndOversizedInputsAreRejected(){
        assertThrows(IOException.class,()->ObtlPdfText.extract("%PDF-invalid".getBytes(),""));
        assertThrows(IOException.class,()->ObtlPdfText.extract(new byte[3*1024*1024+1],""));
    }
    @Test void imageOnlyPdfRequiresReadableReplacement()throws Exception{
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){
            var page=new PDPage();doc.addPage(page);
            try(var c=new PDPageContentStream(doc,page)){
                var image=new java.awt.image.BufferedImage(20,20,java.awt.image.BufferedImage.TYPE_INT_RGB);
                c.drawImage(org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc,image),40,40,100,100);
            }
            doc.save(out);assertEquals("SOURCE_TEXT_REQUIRED_NO_OCR",assertThrows(IOException.class,()->ObtlPdfText.extract(out.toByteArray(),"")).getMessage());
        }
    }
}
