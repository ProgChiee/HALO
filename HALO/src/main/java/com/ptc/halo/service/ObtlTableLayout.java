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
        // Shading/background rectangles are not table borders. Keep thin filled
        // rules; stroked geometry is handled independently.
        if(isPageBackground() || !isThinRule())path.clear();else painted();
    }
    private boolean isThinRule(){
        if(path.size()!=4)return false;
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(Line l:path){minX=Math.min(minX,Math.min(l.x1,l.x2));maxX=Math.max(maxX,Math.max(l.x1,l.x2));minY=Math.min(minY,Math.min(l.y1,l.y2));maxY=Math.max(maxY,Math.max(l.y1,l.y2));}
        // Filled cell backgrounds are not borders; exported thin border rectangles are.
        return maxX-minX<=1.2 || maxY-minY<=1.2;
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

    static ObtlSections.Page read(PDPage page,int number,String plain,int budget,ObtlSections.Page previous)throws IOException{
        var grid=new ObtlTableLayout(page);grid.processPage(page);
        var crop=page.getCropBox();double height=crop.getHeight(),width=crop.getWidth();
        List<Line> horizontal=new ArrayList<>(),vertical=new ArrayList<>();
        for(Line l:grid.lines){
            double x1=Math.min(l.x1,l.x2)-crop.getLowerLeftX(),x2=Math.max(l.x1,l.x2)-crop.getLowerLeftX();
            double y1=height-(Math.max(l.y1,l.y2)-crop.getLowerLeftY()),y2=height-(Math.min(l.y1,l.y2)-crop.getLowerLeftY());
            if(y2-y1<1.2&&x2-x1>8)horizontal.add(new Line(x1,(y1+y2)/2,x2,(y1+y2)/2));
            if(x2-x1<1.2&&y2-y1>8)vertical.add(new Line((x1+x2)/2,y1,(x1+x2)/2,y2));
        }
        boolean chapterText=Pattern.compile("(?i)\\b(?:chapter|unit|weeks?)\\s+\\d").matcher(plain).find();
        String flat=plain.replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
        boolean curriculumHeader=flat.contains("content standards")||flat.contains("essential content")||flat.contains("essential learning")&&Pattern.compile("\\bweeks?\\b").matcher(flat).find();
        boolean tableHeader=flat.contains("content standards")||flat.contains("declarative knowledge")||flat.contains("intended learning outcomes");
        int minimumRules=previous!=null&&!previous.rows().isEmpty()?2:3;
        if(horizontal.size()<minimumRules||vertical.size()<3)
            return new ObtlSections.Page(number,plain,List.of(),curriculumHeader||chapterText&&(tableHeader||!horizontal.isEmpty()||!vertical.isEmpty()));
        if(page.getRotation()!=0)return new ObtlSections.Page(number,plain,List.of(),chapterText||curriculumHeader);
        // Join short cell-border segments into full row rules. Merged week cells may omit the first segment.
        List<Double> ys=cluster(horizontal.stream().map(Line::y1).toList());
        List<Double> xs=cluster(vertical.stream().map(Line::x1).toList());
        int inheritedColumn=-1,inheritedWeek=-1;
        if(previous!=null&&!previous.rows().isEmpty()){
            String[] key=previous.rows().get(previous.rows().size()-1).gridKey().split(":");
            if(key.length==3){
                var prior=Arrays.stream(key[2].split(",")).map(Double::valueOf).toList();
                // Inherit column semantics only when the same physical borders recur.
                var candidates=xs;
                if(prior.stream().allMatch(x->candidates.stream().anyMatch(v->Math.abs(v-x)<1.5))){
                    inheritedColumn=Integer.parseInt(key[0]);inheritedWeek=Integer.parseInt(key[1]);
                    List<Double> matched=new ArrayList<>();for(double x:prior)matched.add(xs.stream().min(Comparator.comparingDouble(v->Math.abs(v-x))).orElseThrow());xs=matched;
                }
            }
        }
        if(xs.size()<3||xs.size()>20||ys.size()>300)return new ObtlSections.Page(number,plain,List.of(),chapterText||curriculumHeader);
        double left=xs.get(0),right=xs.get(xs.size()-1);
        if(right-left<width*.5)return new ObtlSections.Page(number,plain,List.of(),chapterText||curriculumHeader);
        List<Double> rules=new ArrayList<>();
        for(double y:ys){double covered=0,end=-1;
            var parts=horizontal.stream().filter(l->Math.abs(l.y1-y)<1.5).sorted(Comparator.comparingDouble(Line::x1)).toList();
            for(Line l:parts){covered+=Math.max(0,l.x2-Math.max(end,l.x1));end=Math.max(end,l.x2);}
            if(covered>=(right-left)*.75)rules.add(y);
        }
        // Page breaks can leave the top of a ruled row open. Use aligned
        // border endpoints, never the chapter baseline, to retain the entire row.
        double edge=vertical.stream().mapToDouble(Line::y1).min().orElse(0);
        long aligned=vertical.stream().filter(l->Math.abs(l.y1-edge)<1.5).map(Line::x1).distinct().count();
        if(aligned>=3&&!rules.isEmpty()&&edge<rules.get(0)-1.5)rules.add(edge);
        rules.sort(Double::compareTo);
        if(rules.size()<2)return new ObtlSections.Page(number,plain,List.of(),chapterText||curriculumHeader);
        var extractor=new PDFTextStripperByArea();extractor.setSortByPosition(true);
        // A merged cell intentionally belongs to several row regions. PDFBox's global
        // duplicate-glyph suppression would otherwise empty all but its first region.
        extractor.setSuppressDuplicateOverlappingText(false);
        if(2*(rules.size()-1)*(xs.size()-1)>2000)throw new IOException("SOURCE_STRUCTURE_LIMIT");
        for(int r=0;r<rules.size()-1;r++)for(int c=0;c<xs.size()-1;c++){
            double top=rules.get(r),bottom=rules.get(r+1);
            // A merged cell is read using its actual bounding rules, not whichever row contains its baseline.
            double mid=(xs.get(c)+xs.get(c+1))/2;
            double cellTop=ys.stream().filter(y->y<=top+1.5&&covers(horizontal,y,mid)).mapToDouble(Double::doubleValue).max().orElse(top);
            double cellBottom=ys.stream().filter(y->y>=bottom-1.5&&covers(horizontal,y,mid)).mapToDouble(Double::doubleValue).min().orElse(bottom);
            double rowMid=(top+bottom)/2,cellLeft=xs.get(c),cellRight=xs.get(c+1);
            while(c>0&&!verticalAt(vertical,cellLeft,rowMid)){
                final double boundary=cellLeft;cellLeft=xs.stream().filter(x->x<boundary-1.5).mapToDouble(Double::doubleValue).max().orElse(left);if(cellLeft==left)break;
            }
            while(!verticalAt(vertical,cellRight,rowMid)&&cellRight<right){
                final double boundary=cellRight;cellRight=xs.stream().filter(x->x>boundary+1.5).mapToDouble(Double::doubleValue).min().orElse(right);
            }
            extractor.addRegion(r+":"+c,new Rectangle2D.Double(cellLeft,cellTop,cellRight-cellLeft,cellBottom-cellTop));
            extractor.addRegion(r+":"+c+":header",new Rectangle2D.Double(xs.get(c),top,xs.get(c+1)-xs.get(c),bottom-top));
        }
        extractor.extractRegions(page);StringBuilder text=new StringBuilder();List<ObtlSections.Row> rows=new ArrayList<>();int chapterColumn=-1,weekColumn=-1;
        int firstDataRow=0;
        for(int r=0;r<rules.size()-1;r++)for(int c=0;c<xs.size()-1;c++){
            String cell=extractor.getTextForRegion(r+":"+c+":header").replaceAll("\\s+"," ").strip().toLowerCase(Locale.ROOT);
            if(cell.matches("(?:preliminary )?weeks?"))weekColumn=c;
            if(cell.matches("(?:preliminary )?(?:essential )?content(?: standards)?|topics?")){chapterColumn=c;if(firstDataRow==0)firstDataRow=r+1;}
        }
        // Some curricula place topics and supporting knowledge under one merged
        // Essential Learning header. Accept its first column only when it is
        // immediately after an explicitly identified schedule column. Coverage
        // phrases in outcomes/assessment cells never establish this mapping.
        if(chapterColumn<0&&weekColumn>=0&&weekColumn+1<xs.size()-1){
            int content=weekColumn+1;
            for(int r=0;r<rules.size()-1;r++){
                String header=extractor.getTextForRegion(r+":"+content).replaceAll("\\s+"," ").strip();
                if(header.equalsIgnoreCase("Essential Learning")){chapterColumn=content;firstDataRow=r+1;break;}
            }
        }
        boolean headerIdentified=chapterColumn>=0;
        // Identify the content column from chapter labels, never from a hard-coded course layout.
        for(int r=0;r<rules.size()-1;r++)for(int c=0;c<xs.size()-1;c++){
            String cell=extractor.getTextForRegion(r+":"+c).replaceAll("\\s+"," ").strip();
            // A week reference alone may be examination coverage, not a topic column.
            if(cell.toLowerCase(Locale.ROOT).matches("weeks?\\s+.*"))continue;
            if(CHAPTER.matcher(cell).matches()&&!headerIdentified){if(chapterColumn!=-1&&chapterColumn!=c)return new ObtlSections.Page(number,plain,List.of(),true);chapterColumn=c;}
        }
        if(chapterColumn<0){
            // Continuation pages identify the same content column from the repeated table header.
            for(int c=0;c<xs.size()-1;c++)for(int r=0;r<Math.min(2,rules.size()-1);r++)
                if(extractor.getTextForRegion(r+":"+c).replaceAll("\\s+"," ").toLowerCase(Locale.ROOT).contains("content standards"))chapterColumn=c;
        }
        if(chapterColumn<0)chapterColumn=inheritedColumn;
        if(weekColumn<0)weekColumn=inheritedWeek;
        if(chapterColumn<0)return new ObtlSections.Page(number,plain,List.of(),chapterText||curriculumHeader);
        for(int r=firstDataRow;r<rules.size()-1;r++){
            double middle=(rules.get(r)+rules.get(r+1))/2;
            long borders=xs.stream().filter(x->vertical.stream().anyMatch(l->Math.abs(l.x1-x)<1.5&&l.y1<=middle&&l.y2>=middle)).count();
            if(borders<Math.max(3,(xs.size()+1)/2)){
                // A different table (e.g. readings/policies) terminates curriculum ownership.
                rows.add(new ObtlSections.Row(text.length(),text.length(),"",false,left,rules.get(r),right,rules.get(r+1),"END"));break;
            }
            List<String> cells=new ArrayList<>();for(int c=0;c<xs.size()-1;c++)cells.add(extractor.getTextForRegion(r+":"+c).strip());
            String title=cells.get(chapterColumn).replaceAll("\\s+"," ").strip();
            if(title.toLowerCase(Locale.ROOT).matches("(?:essential )?content(?: standards)?|topics?"))continue;
            String joined=String.join("\n",cells);
            // Missing separators must not turn several chapter rows into one confirmable section.
            var headings=Pattern.compile("(?i)\\b(?:chapter|unit|module|lesson|weeks?)\\s+(?:\\d+|[ivxlcdm]+)\\b").matcher(cells.get(chapterColumn));int count=0;while(headings.find())count++;
            if(count>1)return new ObtlSections.Page(number,plain,List.of(),true);
            if(joined.isBlank())continue;int start=text.length();
            text.append(cells.get(chapterColumn)).append('\n');for(int c=0;c<cells.size();c++)if(c!=chapterColumn)text.append(cells.get(c)).append('\n');
            if(text.length()>budget)throw new IOException("SOURCE_TEXT_LIMIT");
            String gridKey=chapterColumn+":"+weekColumn+":"+xs.stream().map(x->Long.toString(Math.round(x))).collect(java.util.stream.Collectors.joining(","));
            String week=weekColumn>=0?cells.get(weekColumn).replaceAll("\\s+"," ").strip():"";
            boolean numberedWeek=week.matches("(?i)(?:weeks?\\s+)?\\d{1,3}(?:\\s*[-–—&]\\s*\\d{1,3})*");
            String label=CHAPTER.matcher(title).matches()?title:numberedWeek&&!title.isBlank()?(week.matches("(?i)weeks?.*")?week:"Week "+week)+" — "+title:"";
            if(label.isEmpty()&&headerIdentified&&weekColumn<0&&!title.isBlank())label=title;
            boolean continuation=title.isBlank()||(r==0&&weekColumn>=0&&week.isBlank()&&inheritedColumn==chapterColumn);
            rows.add(new ObtlSections.Row(start,text.length(),label,continuation,left,rules.get(r),right,rules.get(r+1),gridKey));
        }
        return new ObtlSections.Page(number,text.toString(),rows,false);
    }
    private static boolean covers(List<Line> lines,double y,double x){return lines.stream().anyMatch(l->Math.abs(l.y1-y)<1.5&&l.x1<=x&&l.x2>=x);}
    private static boolean verticalAt(List<Line> lines,double x,double y){return lines.stream().anyMatch(l->Math.abs(l.x1-x)<1.5&&l.y1<=y&&l.y2>=y);}
    private static List<Double> cluster(List<Double> values){var sorted=values.stream().sorted().toList();List<Double> result=new ArrayList<>();for(double value:sorted)if(result.isEmpty()||value-result.get(result.size()-1)>1.5)result.add(value);return result;}
}
