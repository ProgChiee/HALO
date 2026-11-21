package com.ptc.halo.service;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;
import org.w3c.dom.Element;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Bounded reader for the HALO 1.0 plain-data curriculum template. */
public final class ExcelObtl {
    public static final String MIME="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String VERSION="excel-obtl-1";
    static final String SECTION="Section_ID *|Display Order *|Section Type *|Week Start|Week End|Meeting Label|Chapter Number|Official Section Title *|Original Schedule Label|Source PDF Page(s)|Completeness|Transfer Review";
    static final String CONTENT="Section_ID *|Item Order *|Essential Learning / Topic|Declarative Knowledge|Functional / Procedural Knowledge|Intended Learning Outcomes (ILO)|TLA — Synchronous|TLA — Asynchronous|Assessment Tasks|Learning Resources / References|Source PDF Page(s)|Transfer Review";
    static final String OUTCOME="Outcome Code *|Original Outcome Statement *|Original PDF Page(s)|Transfer Review";
    public record Row(int row, List<String> cells) {
        String at(int i){return i<cells.size()?cells.get(i):"";}
    }
    public record Section(String id,String type,String title,Row definition,List<Row> content,boolean complete){}
    public record Evidence(String version,Map<String,String> course,List<Row> pilo,List<Row> cilo,List<Row> details,List<Section> sections){}
    static void check(boolean ok,String field){
        if(!ok)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_OBTL_WORKBOOK: "+field);
    }
    private static List<Element> elements(Element el,String name){
        var nodes=el.getElementsByTagNameNS("*",name);var list=new ArrayList<Element>();
        for(int i=0;i<nodes.getLength();i++)list.add((Element)nodes.item(i));
        return list;
    }
    private static String text(Element el,String tag){
        var value=new StringBuilder();for(var child:elements(el,tag))value.append(child.getTextContent());return value.toString();
    }
    public static Evidence parse(byte[] bytes){
        check(bytes.length<=FileUploadService.MAX_BYTES,"file size");
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
            var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            var parser=factory.newDocumentBuilder();var parts=new HashMap<String,Element>();int total=0;
            for(ZipEntry e;(e=zip.getNextEntry())!=null;){
                String name=e.getName();
                check(parts.size()<100 && name.matches("(?:\\[Content_Types\\]\\.xml|_rels/\\.rels|docProps/(?:app|core)\\.xml|xl/(?:workbook|styles|sharedStrings)\\.xml|xl/(?:theme/theme|tables/table|worksheets/sheet)[0-9]+\\.xml|xl/_rels/workbook\\.xml\\.rels|xl/worksheets/_rels/sheet[0-9]+\\.xml\\.rels)"),"unsupported workbook part");
                byte[] data=zip.readNBytes(2_000_001);total+=data.length;
                check(data.length<=2_000_000 && total<=6_000_000,"expanded size");
                var root=parser.parse(new ByteArrayInputStream(data)).getDocumentElement();
                check(parts.put(name,root)==null,"duplicate workbook part");
                for(var rel:elements(root,"Relationship"))check(!"External".equalsIgnoreCase(rel.getAttribute("TargetMode")),"external relationship");
                for(var part:elements(root,"Override"))check(!part.getAttribute("ContentType").toLowerCase(Locale.ROOT).contains("macroenabled"),"macro workbook");
            }
            check(parts.containsKey("[Content_Types].xml"),"missing content types");
            var strings=new ArrayList<String>();
            if(parts.containsKey("xl/sharedStrings.xml"))for(var si:elements(parts.get("xl/sharedStrings.xml"),"si"))strings.add(text(si,"t"));
            var paths=new HashMap<String,String>();
            for(var rel:elements(parts.get("xl/_rels/workbook.xml.rels"),"Relationship")){
                String target=rel.getAttribute("Target");
                if(rel.getAttribute("Type").endsWith("/worksheet"))check(paths.put(rel.getAttribute("Id"),target.startsWith("/xl/")?target.substring(1):"xl/"+target)==null,"duplicate worksheet relationship");
            }
            var sheets=new HashMap<String,List<Row>>();var used=new HashSet<String>();int chars=0;
            for(var sheet:elements(parts.get("xl/workbook.xml"),"sheet")){
                String name=sheet.getAttribute("name"),path=paths.get(sheet.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id"));
                check(path!=null && path.matches("xl/worksheets/sheet[0-9]+\\.xml") && used.add(path),"worksheet relationship");
                check(name.matches("[A-Z_]{1,32}"),"unsupported worksheet name");
                var rows=new ArrayList<Row>();check(sheets.put(name,rows)==null,"duplicate worksheet");int previous=0;
                for(var row:elements(parts.get(path),"row")){
                    int number=Integer.parseInt(row.getAttribute("r"));check(number>previous && number<=5000,"worksheet row limit");previous=number;
                    var cells=new ArrayList<String>();
                    for(var c:elements(row,"c")){
                        String ref=c.getAttribute("r");check(ref.matches("[A-Z]"+number),"cell coordinate");
                        int col=ref.charAt(0)-'A';check(col>=cells.size(),"duplicate or unordered cell");
                        while(cells.size()<=col)cells.add("");
                        if(!elements(c,"f").isEmpty()){check(name.equals("VALIDATION"),name+" "+ref+" formula");continue;}
                        String v=text(c,"v");
                        String value=switch(c.getAttribute("t")){
                            case "s" -> strings.get(Integer.parseInt(v));
                            case "inlineStr" -> text(c,"t");
                            case "", "n","str","b" -> v;
                            default -> throw new IllegalArgumentException();
                        };
                        chars+=value.length();check(value.length()<=32767 && chars<=250000,"workbook text limit");cells.set(col,value);
                    }
                    if(cells.stream().anyMatch(v->!v.isBlank()))rows.add(new Row(number,List.copyOf(cells)));
                }
            }
            return validate(sheets);
        }catch(ResponseStatusException e){throw e;}catch(Exception e){throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_OBTL_WORKBOOK: unreadable workbook package");}
    }
    private static List<Row> table(Map<String,List<Row>> sheets,String sheet,String header){
        var fields=List.of(header.split("\\|"));var rows=sheets.getOrDefault(sheet,List.of());
        for(int i=0;i<rows.size();i++)if(rows.get(i).cells.size()>=fields.size() && rows.get(i).cells.subList(0,fields.size()).equals(fields))return rows.subList(i+1,rows.size());
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_OBTL_WORKBOOK: "+sheet+" required column headers");
    }
    private static List<Row> outcomes(Map<String,List<Row>> sheets,String sheet){
        var rows=table(sheets,sheet,OUTCOME);var codes=new HashSet<String>();
        for(var r:rows)check(!r.at(0).isBlank() && codes.add(r.at(0)) && !r.at(1).isBlank(),sheet+" row "+r.row+" outcome code or statement");
        check(!rows.isEmpty(),sheet+" missing outcomes");return rows;
    }
    private static int number(String value,String field){
        check(value.matches("[1-9][0-9]{0,3}"),field);return Integer.parseInt(value);
    }
    static Evidence validate(Map<String,List<Row>> sheets){
        check(sheets.getOrDefault("START_HERE",List.of()).stream().anyMatch(r->r.at(0).matches("VERSION 1\\.0(?:\\s.*)?")),"START_HERE supported version 1.0");
        var course=new LinkedHashMap<String,String>();
        for(var r:sheets.getOrDefault("COURSE_INFO",List.of()))if(!r.at(2).isBlank())check(!r.at(1).isBlank() && course.put(r.at(1),r.at(2))==null,"COURSE_INFO row "+r.row+" duplicate field");
        for(String key:List.of("Course Title *","Course Description *"))check(course.containsKey(key),"COURSE_INFO "+key);
        var defs=table(sheets,"SECTIONS",SECTION);check(!defs.isEmpty() && defs.size()<=256,"SECTIONS record count");
        var content=new HashMap<String,List<Row>>();var orders=new HashSet<Integer>();
        for(var r:defs){
            String where="SECTIONS row "+r.row+" ",type=r.at(2);
            check(r.at(0).matches("[A-Za-z0-9_-]{1,64}") && content.put(r.at(0),new ArrayList<>())==null,where+"Section_ID");
            check(orders.add(number(r.at(1),where+"Display Order")),where+"duplicate Display Order");
            check(Set.of("WEEK","CHAPTER","GROUPED_WEEK","ORIENTATION","UNIT","TOPIC","EXAMINATION").contains(type),where+"Section Type");
            check(!r.at(7).isBlank(),where+"Official Section Title");
            boolean exam=r.at(7).toLowerCase(Locale.ROOT).matches(".*\\b(?:midterm|final) (?:exam|examination)\\b.*");
            check(!exam || type.equals("EXAMINATION"),where+"exam classification");
            if(type.equals("WEEK") || type.equals("GROUPED_WEEK") || !r.at(3).isBlank() || !r.at(4).isBlank()){
                int start=number(r.at(3),where+"Week Start"),end=number(r.at(4),where+"Week End");
                check(start<=end && (!type.equals("GROUPED_WEEK") || start<end) && (!type.equals("WEEK") || start==end),where+"week range");
            }
        }
        var items=new HashSet<String>();
        for(var r:table(sheets,"CONTENT",CONTENT)){
            String where="CONTENT row "+r.row+" ",id=r.at(0);
            check(content.containsKey(id),where+"unknown Section_ID");
            check(items.add(id+":"+number(r.at(1),where+"Item Order")),where+"duplicate Item Order");
            check(java.util.stream.IntStream.range(2,10).anyMatch(i->!r.at(i).isBlank()),where+"missing curriculum content");content.get(id).add(r);
        }
        var sections=new ArrayList<Section>();
        for(var r:defs){
            var rows=content.get(r.at(0));check(!rows.isEmpty(),"SECTIONS row "+r.row+" missing CONTENT");
            sections.add(new Section(r.at(0),r.at(2),r.at(7),r,rows,"Complete".equalsIgnoreCase(r.at(10))));
        }
        return new Evidence(VERSION,course,outcomes(sheets,"PILO"),outcomes(sheets,"CILO"),sheets.containsKey("COURSE_DETAILS")?table(sheets,"COURSE_DETAILS","Category *|Item Label *|Official OBTL Text *|Source PDF Page(s)|Transfer Review"):List.of(),sections);
    }
}
