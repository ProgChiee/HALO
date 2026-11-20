package com.ptc.halo.service;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.pdfbox.text.TextPosition;

/** Recover bounded topic/outcome columns within a shared schedule cell. Shared activities
 * have no demonstrated chapter ownership and are deliberately outside the highlighted box. */
final class MergedChapterLayout {
    private static final Pattern CHAPTER=Pattern.compile("(?i)^chapter\\s*(\\d{1,3})(?!\\d).*");
    private record Baseline(double y,String compact,double left,double height) {}
    private record Region(double left,double top,double right,double bottom,String heading,String range,List<Double> columns,boolean continued) {}
    private static final class Glyphs extends PDFTextStripperByArea {
        final List<TextPosition> items=new ArrayList<>();
        boolean unsupported;
        Glyphs() throws IOException {}
        @Override protected void processTextPosition(TextPosition p){
            if(items.size()>=60000)throw new IllegalArgumentException("SOURCE_STRUCTURE_LIMIT");
            if(p.getDir()!=0)unsupported=true;
            items.add(p);
        }
        List<Baseline> lines(double left,double right,double top,double bottom){
            var positions=items.stream().filter(p->p.getX()>=left&&p.getX()<right&&p.getY()>=top&&p.getY()<bottom)
                .sorted(Comparator.comparingDouble(TextPosition::getY)).toList();
            List<Baseline> result=new ArrayList<>();
            for(int i=0;i<positions.size();){
                double y=positions.get(i).getY();int j=i+1;
                while(j<positions.size()&&positions.get(j).getY()-y<1)j++;
                var line=new ArrayList<>(positions.subList(i,j));line.sort(Comparator.comparingDouble(TextPosition::getX));
                StringBuilder text=new StringBuilder();for(var p:line)text.append(p.getUnicode());
                String compact=text.toString().replaceAll("\\s+","");
                if(!compact.isEmpty())result.add(new Baseline(y,compact,line.get(0).getX(),line.stream().mapToDouble(TextPosition::getHeight).max().orElse(0)));
                i=j;
            }
            return result;
        }
    }
    static ObtlSections.Page read(PDPage pdf,ObtlSections.Page original,ObtlSections.Page previous,
            List<ObtlTableLayout.Line> vertical,int budget)throws IOException{
        if(vertical.size()<5||vertical.size()>512)return null;
        double height=pdf.getCropBox().getHeight(),width=pdf.getCropBox().getWidth();
        var glyphs=new Glyphs();glyphs.addRegion("page",new Rectangle2D.Double(0,0,width,height));glyphs.extractRegions(pdf);
        if(glyphs.unsupported)return null;
        var xs=new ArrayList<Double>();
        for(double x:vertical.stream().mapToDouble(ObtlTableLayout.Line::x1).sorted().toArray())
            if(xs.isEmpty()||x-xs.get(xs.size()-1)>1.5)xs.add(x);
        if(xs.size()<5||xs.size()>20)return null;
        List<Region> proposals=new ArrayList<>();ObtlSections.Schedule carry=null;
        // Each band is bounded by the actual schedule-column side, including page-clipped cells.
        var sides=vertical.stream().filter(l->Math.abs(l.x1()-xs.get(0))<1.5).sorted(Comparator.comparingDouble(ObtlTableLayout.Line::y1)).toList();
        double lastBottom=-1;
        for(var side:sides){
            double top=side.y1(),bottom=side.y2();
            if(top<0||bottom>height||top<lastBottom-1.5)continue;
            lastBottom=bottom;
            var edges=xs.stream().filter(x->vertical.stream().anyMatch(l->Math.abs(l.x1()-x)<1.5&&l.y1()<=top+1.5&&l.y2()>=bottom-1.5)).toList();
            if(edges.size()<3)continue;
            var schedule=glyphs.lines(edges.get(0),edges.get(1),top,bottom);
            String scheduleText=schedule.stream().map(Baseline::compact).collect(java.util.stream.Collectors.joining());
            if(scheduleText.matches("\\d{1,2}")&&edges.size()==3){
                var exam=glyphs.lines(edges.get(1),edges.get(2),top,bottom);
                if(exam.size()==1&&exam.get(0).compact.matches("(?i)(MIDTERM|FINAL)EXAMINATION")){
                    String type=exam.get(0).compact.toUpperCase(Locale.ROOT).startsWith("MIDTERM")?"Midterm":"Final";
                    proposals.add(new Region(edges.get(0),top,edges.get(2),bottom,"Week "+scheduleText+" — "+type+" Examination","",edges,false));
                    carry=null;
                }
                continue;
            }
            if(edges.size()<5)continue;
            var key=edges.subList(0,5).stream().map(Math::round).toList();
            String range;
            if(scheduleText.matches("\\d{1,2}[-–—]\\d{1,2}"))range=scheduleText;
            else if(scheduleText.isEmpty()&&side==sides.get(0)&&previous!=null&&previous.schedule()!=null&&key.equals(previous.schedule().columns()))
                range=previous.schedule().range();
            else range="";
            var topics=glyphs.lines(edges.get(1),edges.get(2),top,bottom);
            var heads=topics.stream().filter(l->CHAPTER.matcher(l.compact).matches()&&l.left-edges.get(1)<12).toList();
            if(heads.isEmpty()){carry=null;continue;}
            carry=new ObtlSections.Schedule(range,key);
            List<List<Baseline>> columns=List.of(topics,glyphs.lines(edges.get(2),edges.get(3),top,bottom),
                    glyphs.lines(edges.get(3),edges.get(4),top,bottom));
            for(int i=0;i<heads.size();i++){
                var first=heads.get(i);
                boolean excerpt=i+1==heads.size();
                // A range printed next to the opening chapter certifies this cell's
                // excerpt; its bottom alone does not prove that the chapter is complete.
                boolean followsChapter=i>0 && consecutive(heads.get(i-1),first);
                if(excerpt && !followsChapter && (scheduleText.isEmpty() || range.isEmpty()))continue;
                if(!excerpt && range.isEmpty() && !consecutive(first,heads.get(i+1)))continue;
                double start=cut(columns,first.y,top),end=excerpt?bottom:cut(columns,heads.get(i+1).y,top);
                if(!Double.isFinite(start)||!Double.isFinite(end)||start>=end)continue;
                String number=CHAPTER.matcher(first.compact).replaceFirst("$1");
                boolean continued=first.compact.toLowerCase(Locale.ROOT).contains("(continued)");
                String heading="Chapter "+number+(excerpt?" (opening excerpt)":"");
                if(continued){
                    // An intervening row, changed grid, or unnamed carry-over breaks ownership.
                    if(i!=0 || side!=sides.get(0) || previous==null || previous.rows().isEmpty()
                            || first.y-top>24 || !scheduleText.isEmpty())continue;
                    var last=previous.rows().get(previous.rows().size()-1);
                    if(!last.heading().equals("Chapter "+number+" (opening excerpt)") ||
                            !last.gridKey().equals("recovery:merged|"+range) || previous.schedule()==null ||
                            !key.equals(previous.schedule().columns()))continue;
                    heading=last.heading();
                }
                proposals.add(new Region(edges.get(1),start,edges.get(4),end,heading,range,edges.subList(1,5),continued));
            }
        }
        if(proposals.isEmpty())return carry==null?null:new ObtlSections.Page(original.number(),original.text(),original.rows(),original.ambiguousTable(),carry);
        if(proposals.size()>64)return null;
        // Preserve existing confirmable automatic rows. This pass is for unresolved pages.
        if(!original.ambiguousTable()&&original.rows().stream().anyMatch(r->!r.heading().isBlank()))
            return new ObtlSections.Page(original.number(),original.text(),original.rows(),false,carry);
        var strip=new PDFTextStripperByArea();strip.setSortByPosition(true);
        for(int i=0;i<proposals.size();i++){
            var r=proposals.get(i);
            for(int c=1;c<r.columns.size();c++)strip.addRegion(i+"/"+c,new Rectangle2D.Double(r.columns.get(c-1),r.top,
                r.columns.get(c)-r.columns.get(c-1),r.bottom-r.top));
        }
        strip.extractRegions(pdf);
        var text=new StringBuilder();var rows=new ArrayList<ObtlSections.Row>();
        for(int i=0;i<proposals.size();i++){
            var r=proposals.get(i);int start=text.length();
            for(int c=1;c<r.columns.size();c++)text.append(strip.getTextForRegion(i+"/"+c)).append('\n');
            if(text.length()>budget)throw new IOException("SOURCE_TEXT_LIMIT");
            rows.add(new ObtlSections.Row(start,text.length(),r.heading,r.continued,r.left,r.top,r.right,r.bottom,"recovery:merged|"+r.range));
        }
        if(Math.abs(proposals.get(proposals.size()-1).bottom-lastBottom)>1.5)
            rows.add(new ObtlSections.Row(text.length(),text.length(),"",false,0,0,0,0,"END"));
        return new ObtlSections.Page(original.number(),text.toString(),rows,true,carry);
    }
    private static boolean consecutive(Baseline first,Baseline next){
        var a=CHAPTER.matcher(first.compact);var b=CHAPTER.matcher(next.compact);
        return a.matches() && b.matches() && Integer.parseInt(b.group(1))==Integer.parseInt(a.group(1))+1;
    }
    // All three columns must start a distinct block at the same heading. Choose a
    // cut in their common whitespace; a single heading baseline is insufficient.
    private static double cut(List<List<Baseline>> columns,double anchor,double top){
        double lower=top,upper=Double.POSITIVE_INFINITY;
        for(var column:columns){
            var near=column.stream().filter(l->Math.abs(l.y-anchor)<=8).toList();
            var starts=near.stream().filter(l->column.stream().noneMatch(p->p.y<l.y&&l.y-p.y<25)).toList();
            if(starts.size()!=1)return Double.NaN;
            var line=starts.get(0);
            var prior=column.stream().filter(l->l.y<line.y).reduce((a,b)->b);
            if(prior.isPresent())lower=Math.max(lower,prior.get().y+3);
            upper=Math.min(upper,line.y-line.height-2);
        }
        return upper>lower?(upper+lower)/2:lower==top && upper>=top-2?top:Double.NaN;
    }
}
