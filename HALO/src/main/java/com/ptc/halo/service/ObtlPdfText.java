package com.ptc.halo.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Offline extraction core. Must run in a resource-isolated worker before exposing to uploads. */
public final class ObtlPdfText {
    public record Extracted(String checksum, ObtlSections.Detection detection) {}
    private static final Set<String> FORBIDDEN = Set.of("OpenAction", "AA", "JS", "JavaScript",
        "EmbeddedFiles", "EmbeddedFile", "Filespec", "RichMedia", "XFA", "Launch", "GoToR", "SubmitForm", "ImportData");
    private ObtlPdfText() {}
    public static Extracted extract(byte[] bytes, String moduleTitle) throws IOException {
        if (bytes == null || bytes.length < 5 || bytes.length > 3*1024*1024)
            throw new IOException("SOURCE_SIZE_INVALID");
        if (!new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
            throw new IOException("SOURCE_TYPE_INVALID");
        try (var document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted() || !document.getCurrentAccessPermission().canExtractContent())
                throw new IOException("SOURCE_ENCRYPTED_OR_RESTRICTED");
            if (document.getNumberOfPages() < 1 || document.getNumberOfPages() > 200)
                throw new IOException("SOURCE_PAGE_LIMIT");
            inspect(document.getDocumentCatalog().getCOSObject());
            List<ObtlSections.Page> pages = new ArrayList<>();
            int remaining = ObtlSections.MAX_CHARS;
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int page=1; page<=document.getNumberOfPages(); page++) {
                stripper.setStartPage(page); stripper.setEndPage(page);
                var writer = new LimitedWriter(remaining);
                stripper.writeText(document,writer);
                var extracted=ObtlTableLayout.read(document.getPage(page-1),page,writer.toString(),remaining);
                remaining-=extracted.text().length();
                pages.add(extracted);
            }
            if (pages.stream().allMatch(p->p.text().isBlank())) throw new IOException("SOURCE_TEXT_REQUIRED_NO_OCR");
            String checksum;
            try {checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
            catch (java.security.NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
            return new Extracted(checksum,ObtlSections.detect(pages,moduleTitle));
        }
    }
    private static void inspect(COSBase root) throws IOException {
        Set<COSBase> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<COSBase> pending=new ArrayDeque<>(); pending.add(root);
        int count=0;
        while(!pending.isEmpty()) {
            COSBase value=pending.removeFirst();
            if(!visited.add(value))continue;
            if(++count>100_000)throw new IOException("SOURCE_STRUCTURE_LIMIT");
            if(value instanceof COSObject object){if(object.getObject()!=null)pending.add(object.getObject());}
            else if(value instanceof COSDictionary dictionary){
                for(COSName key:dictionary.keySet()) {
                    if(FORBIDDEN.contains(key.getName()))throw new IOException("SOURCE_ACTIVE_CONTENT_REJECTED");
                    COSBase child=dictionary.getItem(key);
                    if(child!=null)pending.add(child);
                }
            } else if(value instanceof COSArray array){for(COSBase child:array)if(child!=null)pending.add(child);}
            else if(value instanceof COSName name && FORBIDDEN.contains(name.getName()))
                throw new IOException("SOURCE_ACTIVE_CONTENT_REJECTED");
            if(pending.size()>100_000)throw new IOException("SOURCE_STRUCTURE_LIMIT");
        }
    }
    private static final class LimitedWriter extends Writer {
        private final int limit; private final StringBuilder text=new StringBuilder();
        LimitedWriter(int limit){this.limit=limit;}
        @Override public void write(char[] chars,int offset,int length)throws IOException{
            if(length>limit-text.length())throw new IOException("SOURCE_TEXT_LIMIT");
            text.append(chars,offset,length);
        }
        @Override public void flush(){}
        @Override public void close(){}
        @Override public String toString(){return text.toString();}
    }
}
