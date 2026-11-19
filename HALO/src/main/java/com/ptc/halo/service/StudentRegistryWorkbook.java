package com.ptc.halo.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Bounded OOXML reader. Only a single plain-data worksheet is accepted. */
@Component
public class StudentRegistryWorkbook {
    public static final int MAX_BYTES=2*1024*1024, MAX_ROWS=1000;
    public record Row(int row, String studentId, String fullName, String email, String course) {}
    static RuntimeException invalid() { return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_REGISTRY_WORKBOOK"); }
    public List<Row> read(MultipartFile file) {
        if(file==null || file.isEmpty() || file.getSize()>MAX_BYTES || file.getOriginalFilename()==null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw invalid();
        try(InputStream raw=file.getInputStream(); ZipInputStream zip=new ZipInputStream(raw)) {
            Map<String,byte[]> entries=new HashMap<>(); int total=0;
            for(ZipEntry e;(e=zip.getNextEntry())!=null;) {
                String name=e.getName();
                String lower=name.toLowerCase(Locale.ROOT);
                if(entries.size()>=100 || name.contains("..") || name.startsWith("/") || name.contains("\\") || lower.endsWith(".bin") || lower.contains("externallinks") || lower.contains("embeddings") || lower.contains("connections") || lower.contains("querytables")) throw invalid();
                if(e.isDirectory()) continue;
                byte[] bytes=zip.readNBytes(4*1024*1024+1); total+=bytes.length;
                if(bytes.length>4*1024*1024 || total>8*1024*1024 || entries.put(name,bytes)!=null) throw invalid();
                if(name.endsWith(".rels")) {
                    NodeList relations=xml(bytes).getElementsByTagNameNS("*","Relationship");
                    for(int i=0;i<relations.getLength();i++) {
                        Element relation=(Element)relations.item(i);
                        if("External".equalsIgnoreCase(relation.getAttribute("TargetMode"))) {
                            // Excel auto-links email cells. Ignore this metadata; never resolve or render targets.
                            boolean emailLink=name.matches("xl/worksheets/_rels/sheet[0-9]+\\.xml\\.rels")
                                    && relation.getAttribute("Type").equals("http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink")
                                    && relation.getAttribute("Target").regionMatches(true,0,"mailto:",0,7);
                            if(!emailLink)throw invalid();
                        }
                    }
                }
            }
            if(entries.containsKey("[Content_Types].xml") && new String(entries.get("[Content_Types].xml"),StandardCharsets.UTF_8).toLowerCase(Locale.ROOT).contains("macroenabled")) throw invalid();
            if(!entries.containsKey("[Content_Types].xml") || !entries.containsKey("xl/workbook.xml")) throw invalid();
            if(xml(entries.get("xl/workbook.xml")).getElementsByTagNameNS("*","sheet").getLength()!=1) throw invalid();
            List<String> sheets=entries.keySet().stream().filter(n->n.matches("xl/worksheets/sheet[0-9]+\\.xml")).toList();
            if(sheets.size()!=1) throw invalid();
            List<String> strings=new ArrayList<>();
            if(entries.containsKey("xl/sharedStrings.xml")) {
                NodeList items=xml(entries.get("xl/sharedStrings.xml")).getElementsByTagNameNS("*","si");
                if(items.getLength()>5000) throw invalid();
                for(int i=0;i<items.getLength();i++) strings.add(text((Element)items.item(i)));
            }
            Document doc=xml(entries.get(sheets.get(0)));
            if(doc.getElementsByTagNameNS("*","f").getLength()>0 || doc.getElementsByTagNameNS("*","mergeCell").getLength()>0) throw invalid();
            NodeList rows=doc.getElementsByTagNameNS("*","row");
            if(rows.getLength()<1) throw invalid();
            List<Row> result=new ArrayList<>(); Set<Integer> numbers=new HashSet<>();
            for(int i=0;i<rows.getLength();i++) {
                Element row=(Element)rows.item(i); int number=Integer.parseInt(row.getAttribute("r"));
                if(number<1 || number>1048576 || !numbers.add(number)) throw invalid();
                String[] values={"","","",""};Set<String> used=new HashSet<>();
                NodeList cells=row.getElementsByTagNameNS("*","c");
                for(int j=0;j<cells.getLength();j++) {
                    Element c=(Element)cells.item(j);String ref=c.getAttribute("r");
                    if(!ref.matches("[A-Z]{1,3}"+number) || !used.add(ref)) throw invalid();
                    String letters=ref.substring(0,ref.length()-Integer.toString(number).length());
                    int column=0;for(char letter:letters.toCharArray())column=column*26+(letter-'A'+1);
                    if(column>16384)throw invalid();int col=column-1;
                    String type=c.getAttribute("t");String value;
                    if(type.equals("inlineStr"))value=text(c);
                    else { NodeList v=c.getElementsByTagNameNS("*","v");value=v.getLength()==0?"":v.item(0).getTextContent();
                        if(type.equals("s"))value=v.getLength()==0?"":strings.get(Integer.parseInt(value));else if(!type.isEmpty()&&!Set.of("str","n","b","e","d").contains(type))throw invalid(); }
                    // Excel writes style-only cells far outside the data rectangle. Ignore only empty extras.
                    if(value.length()>32767)throw invalid();
                    if(col>=4){if(!value.isBlank())throw invalid();continue;}
                    values[col]=value.strip();
                }
                if(i==0) {if(number!=1 || !Arrays.equals(values,new String[]{"Student_ID","Full_Name","Institutional_Email","Course"}))throw invalid();}
                else if(Arrays.stream(values).anyMatch(v->!v.isBlank())) {
                    if(result.size()>=MAX_ROWS)throw invalid();
                    result.add(new Row(number,values[0],values[1],values[2],values[3]));
                }
            }
            if(result.isEmpty())throw invalid();return result;
        } catch(Exception error) { throw invalid(); }
    }
    private static Document xml(byte[] bytes) throws Exception {
        var f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        var b=f.newDocumentBuilder();b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler(){@Override public void fatalError(org.xml.sax.SAXParseException e)throws org.xml.sax.SAXException{throw e;}});
        return b.parse(new ByteArrayInputStream(bytes));
    }
    private static String text(Element e) {StringBuilder b=new StringBuilder();NodeList t=e.getElementsByTagNameNS("*","t");for(int i=0;i<t.getLength();i++)b.append(t.item(i).getTextContent());return b.toString();}
    public byte[] template() {
        try(var out=new ByteArrayOutputStream();var zip=new ZipOutputStream(out)) {
            Map<String,String> parts=new LinkedHashMap<>();
            parts.put("[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            parts.put("_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            parts.put("xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Registry\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            parts.put("xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            StringBuilder sheet=new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            String[][] data={{"Student_ID","Full_Name","Institutional_Email","Course"},{"REPLACE WITH 23AHRT-0168","Replace with official full name","Replace with official school email","AHRT"}};
            for(int r=0;r<data.length;r++){sheet.append("<row r=\"").append(r+1).append("\">");for(int c=0;c<4;c++)sheet.append("<c r=\"").append((char)('A'+c)).append(r+1).append("\" t=\"inlineStr\"><is><t>").append(data[r][c]).append("</t></is></c>");sheet.append("</row>");}
            parts.put("xl/worksheets/sheet1.xml",sheet.append("</sheetData></worksheet>").toString());
            for(var part:parts.entrySet()){zip.putNextEntry(new ZipEntry(part.getKey()));zip.write(part.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}zip.finish();return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Template unavailable");}
    }
}
