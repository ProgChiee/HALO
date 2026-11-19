package com.ptc.halo.service;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import java.awt.geom.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** Conservative ruled-table reader. Coordinates are PDF crop-box-relative, top down. */
final class ObtlTableLayout extends PDFGraphicsStreamEngine {
    private static final Pattern CHAPTER=Pattern.compile("(?i)^(?:chapter|unit|module|lesson|weeks?)\\s+(?:\\d+|[ivxlcdm]+|one|two|three|four|five)(?:\\s*(?:&|and|[-–—])\\s*\\d+)*\\b.*");
    private record Line(double x1,double y1,double x2,double y2) {}
    private final List<Line> lines=new ArrayList<>(),path=new ArrayList<>();
    private Point2D current,origin;private int operations;
    ObtlTableLayout(PDPage page){super(page);}
    private void segment(Point2D a,Point2D b)throws IOException{
        if(++operations>20000)throw new IOException("SOURCE_STRUCTURE_LIMIT");
        if(a!=null&&b!=null)path.add(new Line(a.getX(),a.getY(),b.getX(),b.getY()));
    }
    @Override public void appendRectangle(Point2D a,Point2D b,Point2D c,Point2D d)throws IOException{segment(a,b);segment(b,c);segment(c,d);segment(d,a);current=a;origin=a;}
    @Override public void moveTo(float x,float y){current=new Point2D.Float(x,y);origin=current;}
    @Override public void lineTo(float x,float y)throws IOException{Point2D next=new Point2D.Float(x,y);segment(current,next);current=next;}
    @Override public void curveTo(float a,float b,float c,float d,float e,float f){current=new Point2D.Float(e,f);}
    @Override public Point2D getCurrentPoint(){return current;}
    @Override public void closePath()throws IOException{segment(current,origin);current=origin;}
    private void painted(){lines.addAll(path);path.clear();}
    @Override public void strokePath(){painted();}
    @Override public void fillPath(int rule){
        // Exporters paint a page-sized rectangle behind ordinary prose. Its edges
        // are not table rules. Preserve strokes and all smaller filled geometry.
        if(isPageBackground())path.clear();else painted();
    }
    private boolean isPageBackground(){
        var box=getPage().getCropBox();
        if(path.size()!=4)return false;
        boolean top=false,bottom=false,left=false,right=false;
        for(Line l:path){
            double x1=Math.min(l.x1,l.x2),x2=Math.max(l.x1,l.x2);
            double y1=Math.min(l.y1,l.y2),y2=Math.max(l.y1,l.y2);
            boolean fullWidth=Math.abs(x1-box.getLowerLeftX())<=1&&Math.abs(x2-box.getUpperRightX())<=1;
            boolean fullHeight=Math.abs(y1-box.getLowerLeftY())<=1&&Math.abs(y2-box.getUpperRightY())<=1;
            top|=fullWidth&&Math.abs(y1-box.getUpperRightY())<=1&&Math.abs(y2-y1)<=1;
            bottom|=fullWidth&&Math.abs(y1-box.getLowerLeftY())<=1&&Math.abs(y2-y1)<=1;
            left|=fullHeight&&Math.abs(x1-box.getLowerLeftX())<=1&&Math.abs(x2-x1)<=1;
            right|=fullHeight&&Math.abs(x1-box.getUpperRightX())<=1&&Math.abs(x2-x1)<=1;
        }
        return top&&bottom&&left&&right;
    }
    @Override public void fillAndStrokePath(int rule){painted();}
    @Override public void endPath(){path.clear();}
    @Override public void clip(int rule){}
    @Override public void drawImage(PDImage image){}
    @Override public void shadingFill(org.apache.pdfbox.cos.COSName name){}

    static ObtlSections.Page read(PDPage page,int number,String plain,int budget)throws IOException{
        var grid=new ObtlTableLayout(page);grid.processPage(page);
        var crop=page.getCropBox();double height=crop.getHeight(),width=crop.getWidth();
        List<Line> horizontal=new ArrayList<>(),vertical=new ArrayList<>();
        for(Line l:grid.lines){
            double x1=Math.min(l.x1,l.x2)-crop.getLowerLeftX(),x2=Math.max(l.x1,l.x2)-crop.getLowerLeftX();
            double y1=height-(Math.max(l.y1,l.y2)-crop.getLowerLeftY()),y2=height-(Math.min(l.y1,l.y2)-crop.getLowerLeftY());
            if(y2-y1<1.2&&x2-x1>8)horizontal.add(new Line(x1,(y1+y2)/2,x2,(y1+y2)/2));
            if(x2-x1<1.2&&y2-y1>8)vertical.add(new Line((x1+x2)/2,y1,(x1+x2)/2,y2));
        }
        boolean chapterText=Pattern.compile("(?i)\\b(?:chapter|unit|week)\\s+\\d").matcher(plain).find();
        String flat=plain.replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
        boolean tableHeader=flat.contains("content standards")||flat.contains("declarative knowledge")||flat.contains("intended learning outcomes");
        if(horizontal.size()<3||vertical.size()<3)
            return new ObtlSections.Page(number,plain,List.of(),chapterText&&(tableHeader||!horizontal.isEmpty()||!vertical.isEmpty()));
        if(page.getRotation()!=0)return new ObtlSections.Page(number,plain,List.of(),chapterText);
        // Join short cell-border segments into full row rules. Merged week cells may omit the first segment.
        List<Double> ys=cluster(horizontal.stream().map(Line::y1).toList());
        List<Double> xs=cluster(vertical.stream().map(Line::x1).toList());
        if(xs.size()<3||xs.size()>20||ys.size()>300)return new ObtlSections.Page(number,plain,List.of(),chapterText);
        double left=xs.get(0),right=xs.get(xs.size()-1);
        if(right-left<width*.5)return new ObtlSections.Page(number,plain,List.of(),chapterText);
        List<Double> rules=new ArrayList<>();
        for(double y:ys){double covered=0,end=-1;
            var parts=horizontal.stream().filter(l->Math.abs(l.y1-y)<1.5).sorted(Comparator.comparingDouble(Line::x1)).toList();
            for(Line l:parts){covered+=Math.max(0,l.x2-Math.max(end,l.x1));end=Math.max(end,l.x2);}
            if(covered>=(right-left)*.75)rules.add(y);
        }
        if(rules.size()<2)return new ObtlSections.Page(number,plain,List.of(),chapterText);
        var extractor=new PDFTextStripperByArea();extractor.setSortByPosition(true);
        // A merged cell intentionally belongs to several row regions. PDFBox's global
        // duplicate-glyph suppression would otherwise empty all but its first region.
        extractor.setSuppressDuplicateOverlappingText(false);
        if((rules.size()-1)*(xs.size()-1)>2000)throw new IOException("SOURCE_STRUCTURE_LIMIT");
        for(int r=0;r<rules.size()-1;r++)for(int c=0;c<xs.size()-1;c++){
            double top=rules.get(r),bottom=rules.get(r+1);
            // A merged cell is read using its actual bounding rules, not whichever row contains its baseline.
            double mid=(xs.get(c)+xs.get(c+1))/2;
            double cellTop=ys.stream().filter(y->y<=top+1.5&&covers(horizontal,y,mid)).mapToDouble(Double::doubleValue).max().orElse(top);
            double cellBottom=ys.stream().filter(y->y>=bottom-1.5&&covers(horizontal,y,mid)).mapToDouble(Double::doubleValue).min().orElse(bottom);
            extractor.addRegion(r+":"+c,new Rectangle2D.Double(xs.get(c),cellTop,xs.get(c+1)-xs.get(c),cellBottom-cellTop));
        }
        extractor.extractRegions(page);StringBuilder text=new StringBuilder();List<ObtlSections.Row> rows=new ArrayList<>();int chapterColumn=-1;
        // Identify the content column from chapter labels, never from a hard-coded course layout.
        boolean chapterLabels=Pattern.compile("(?i)\\b(?:chapter|unit|module|lesson)\\s+(?:\\d+|[ivxlcdm]+)\\b").matcher(plain).find();
        for(int r=0;r<rules.size()-1;r++)for(int c=0;c<xs.size()-1;c++){
            String cell=extractor.getTextForRegion(r+":"+c).replaceAll("\\s+"," ").strip();
            if(chapterLabels&&cell.toLowerCase(Locale.ROOT).matches("weeks?\\s+.*"))continue;
            if(CHAPTER.matcher(cell).matches()){if(chapterColumn!=-1&&chapterColumn!=c)return new ObtlSections.Page(number,plain,List.of(),true);chapterColumn=c;}
        }
        if(chapterColumn<0){
            // Continuation pages identify the same content column from the repeated table header.
            for(int c=0;c<xs.size()-1;c++)for(int r=0;r<Math.min(2,rules.size()-1);r++)
                if(extractor.getTextForRegion(r+":"+c).replaceAll("\\s+"," ").toLowerCase(Locale.ROOT).contains("content standards"))chapterColumn=c;
        }
        if(chapterColumn<0)return new ObtlSections.Page(number,plain,List.of(),chapterText);
        for(int r=0;r<rules.size()-1;r++){
            List<String> cells=new ArrayList<>();for(int c=0;c<xs.size()-1;c++)cells.add(extractor.getTextForRegion(r+":"+c).strip());
            String title=cells.get(chapterColumn).replaceAll("\\s+"," ").strip();
            if(title.toLowerCase(Locale.ROOT).contains("content standards"))continue;
            String joined=String.join("\n",cells);
            // Missing separators must not turn several chapter rows into one confirmable section.
            var headings=Pattern.compile("(?i)\\b(?:chapter|unit|module|lesson|weeks?)\\s+(?:\\d+|[ivxlcdm]+)\\b").matcher(cells.get(chapterColumn));int count=0;while(headings.find())count++;
            if(count>1)return new ObtlSections.Page(number,plain,List.of(),true);
            if(joined.isBlank())continue;int start=text.length();
            text.append(cells.get(chapterColumn)).append('\n');for(int c=0;c<cells.size();c++)if(c!=chapterColumn)text.append(cells.get(c)).append('\n');
            if(text.length()>budget)throw new IOException("SOURCE_TEXT_LIMIT");
            String gridKey=chapterColumn+":"+xs.stream().map(x->Long.toString(Math.round(x))).collect(java.util.stream.Collectors.joining(","));
            rows.add(new ObtlSections.Row(start,text.length(),CHAPTER.matcher(title).matches()?title:"",title.isBlank(),left,rules.get(r),right,rules.get(r+1),gridKey));
        }
        return new ObtlSections.Page(number,text.toString(),rows,false);
    }
    private static boolean covers(List<Line> lines,double y,double x){return lines.stream().anyMatch(l->Math.abs(l.y1-y)<1.5&&l.x1<=x&&l.x2>=x);}
    private static List<Double> cluster(List<Double> values){var sorted=values.stream().sorted().toList();List<Double> result=new ArrayList<>();for(double value:sorted)if(result.isEmpty()||value-result.get(result.size()-1)>1.5)result.add(value);return result;}
}
