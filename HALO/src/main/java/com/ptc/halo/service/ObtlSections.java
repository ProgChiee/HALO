package com.ptc.halo.service;

import java.util.*;
import java.util.regex.Pattern;

/** Deterministic proposals only. Neither matching nor confirmation proves instructional sufficiency. */
public final class ObtlSections {
    public static final String VERSION = "obtl-table-spans-v3";
    public static final int MAX_CHARS = 600_000;
    private static final Pattern HEADING = Pattern.compile(
        "(?im)^[\\t ]*(?:\\d{1,2}[\\t ]+)?((?:chapter|unit|module|lesson|weeks?)\\s+"
        + "(?:\\d{1,3}|[ivxlcdm]+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)"
        + "(?:[\\t ]*[-–—][\\t ]*\\d{1,3})?)(?=[\\s:.-]|$)[^\\r\\n]*");
    public record Row(int start, int end, String heading, boolean continuation,
                      double left, double top, double right, double bottom, String gridKey) {}
    public record Page(int number, String text, List<Row> rows, boolean ambiguousTable) {
        public Page(int number, String text) { this(number,text,List.of(),false); }
        public Page { rows=rows==null?List.of():List.copyOf(rows); }
    }
    /** Offsets are UTF-16 offsets into the immutable extracted page text, end exclusive. */
    public record Span(int page, int start, int end) {}
    public record Section(String id, String heading, List<Span> spans, List<String> warnings,
                          int titleMatches) {}
    public record Detection(String version, List<Page> pages, List<Section> sections,
                            List<String> warnings) {}
    private record Marker(int pageIndex, int offset, String heading) {}
    private ObtlSections() {}

    public static Detection detect(List<Page> supplied, String moduleTitle) {
        if (supplied == null || supplied.isEmpty() || supplied.size() > 200)
            throw new IllegalArgumentException("INVALID_SOURCE_PAGES");
        List<Page> pages = List.copyOf(supplied);
        int total = 0;
        List<Marker> markers = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            Page page = pages.get(i);
            if (page.number() != i + 1 || page.text() == null)
                throw new IllegalArgumentException("INVALID_SOURCE_PAGES");
            total = Math.addExact(total, page.text().length());
            if (total > MAX_CHARS) throw new IllegalArgumentException("SOURCE_TEXT_LIMIT");
            var match = HEADING.matcher(page.text());
            while (match.find()) {
                if (markers.size() >= 256) throw new IllegalArgumentException("SOURCE_SECTION_LIMIT");
                markers.add(new Marker(i, match.start(), match.group().strip()));
            }
        }
        if (total == 0) throw new IllegalArgumentException("SOURCE_TEXT_REQUIRED");
        if(pages.stream().anyMatch(p->!p.rows().isEmpty()||p.ambiguousTable()))
            return tableSections(pages,moduleTitle);
        List<Section> sections = new ArrayList<>();
        Set<String> titleTerms = words(moduleTitle);
        Map<String, Integer> frequency = new HashMap<>();
        for (Marker marker : markers) frequency.merge(marker.heading().toLowerCase(Locale.ROOT), 1, Integer::sum);
        for (int i = 0; i < markers.size(); i++) {
            Marker first = markers.get(i);
            Marker next = i + 1 < markers.size() ? markers.get(i + 1) : null;
            int lastPage = next == null ? pages.size() - 1 : next.pageIndex();
            List<Span> spans = new ArrayList<>();
            for (int p = first.pageIndex(); p <= lastPage; p++) {
                int start = p == first.pageIndex() ? first.offset() : 0;
                int end = next != null && p == next.pageIndex() ? next.offset() : pages.get(p).text().length();
                if (end > start) spans.add(new Span(p + 1, start, end));
            }
            var warnings = new ArrayList<String>();
            warnings.add("PROFESSOR_BOUNDARY_REVIEW_REQUIRED");
            warnings.add("INSTRUCTIONAL_SUFFICIENCY_NOT_VERIFIED");
            if (frequency.get(first.heading().toLowerCase(Locale.ROOT)) > 1) warnings.add("REPEATED_HEADING");
            if (next == null) warnings.add("FINAL_SECTION_END_UNCONFIRMED");
            if (first.heading().matches(".*\\.{3,}.*")) warnings.add("POSSIBLE_CONTENTS_ENTRY");
            // Match module title terms against the complete proposed section, including topics/outcomes.
            StringBuilder sectionText=new StringBuilder();
            for(Span span:spans)sectionText.append(pages.get(span.page()-1).text(),span.start(),span.end());
            int score = (int) words(sectionText.toString()).stream().filter(titleTerms::contains).count();
            sections.add(new Section("p" + (first.pageIndex()+1) + "o" + first.offset(), first.heading(),
                List.copyOf(spans), List.copyOf(warnings), score));
        }
        return new Detection(VERSION, pages, List.copyOf(sections),
            List.of("TABLE_READING_ORDER_REQUIRES_REVIEW", "NUMBERS_DO_NOT_ESTABLISH_WEEK_CHAPTER_EQUIVALENCE"));
    }

    /** Table rows are indivisible ownership units; a heading's baseline is not its boundary. */
    private static Detection tableSections(List<Page> pages,String title) {
        List<Section> sections=new ArrayList<>();List<Span> spans=new ArrayList<>();
        List<String> warnings=new ArrayList<>();String heading=null,id=null,gridKey=null;
        for(Page page:pages) {
            if(page.ambiguousTable()) {
                if(heading!=null)warnings.add("AMBIGUOUS_TABLE_BOUNDARY");
                sections.add(new Section("ambiguous-p"+page.number(),"Ambiguous table — review PDF page "+page.number(),
                    List.of(),List.of("AMBIGUOUS_TABLE_BOUNDARY","PROFESSOR_BOUNDARY_REVIEW_REQUIRED"),0));
                continue;
            }
            if(page.rows().isEmpty()) {
                // Never infer that non-table pages following a table belong to its final chapter.
                if(heading!=null)warnings.add("TABLE_CONTINUATION_UNCONFIRMED");
                if(HEADING.matcher(page.text()).find())sections.add(new Section("unruled-p"+page.number(),"Unisolated source — review PDF page "+page.number(),List.of(),List.of("AMBIGUOUS_TABLE_BOUNDARY","PROFESSOR_BOUNDARY_REVIEW_REQUIRED"),0));
                continue;
            }
            for(Row row:page.rows()) {
                if(!row.heading().isBlank()) {
                    if(heading!=null)sections.add(tableSection(id,heading,spans,warnings,pages,title));
                    spans=new ArrayList<>();warnings=new ArrayList<>();heading=row.heading();
                    id="p"+page.number()+"r"+row.start();
                    gridKey=row.gridKey();
                } else if(!row.continuation()) {
                    if(heading!=null)sections.add(tableSection(id,heading,spans,warnings,pages,title));
                    heading=null;spans=new ArrayList<>();warnings=new ArrayList<>();continue;
                }
                if(heading!=null) {
                    if(row.continuation()&&!Objects.equals(gridKey,row.gridKey())){warnings.add("AMBIGUOUS_TABLE_BOUNDARY");continue;}
                    if(row.continuation())warnings.add("TABLE_CONTINUATION_REQUIRES_REVIEW");
                    spans.add(new Span(page.number(),row.start(),row.end()));
                }
            }
        }
        if(heading!=null)sections.add(tableSection(id,heading,spans,warnings,pages,title));
        if(sections.size()>256)throw new IllegalArgumentException("SOURCE_SECTION_LIMIT");
        return new Detection(VERSION,pages,List.copyOf(sections),List.of("TABLE_ROW_BOUNDARIES","NUMBERS_DO_NOT_ESTABLISH_WEEK_CHAPTER_EQUIVALENCE"));
    }
    private static Section tableSection(String id,String heading,List<Span> spans,List<String> warnings,List<Page> pages,String title){
        Set<String> flags=new LinkedHashSet<>(warnings);flags.add("PROFESSOR_BOUNDARY_REVIEW_REQUIRED");flags.add("INSTRUCTIONAL_SUFFICIENCY_NOT_VERIFIED");
        StringBuilder text=new StringBuilder();for(Span s:spans)text.append(pages.get(s.page()-1).text(),s.start(),s.end());
        int score=(int)words(text.toString()).stream().filter(words(title)::contains).count();
        return new Section(id,heading,List.copyOf(spans),List.copyOf(flags),score);
    }

    public static String text(Detection document, Section section) {
        if (!document.sections().contains(section)) throw new IllegalArgumentException("UNKNOWN_SOURCE_SECTION");
        StringBuilder result = new StringBuilder();
        for (Span span : section.spans()) {
            Page page = document.pages().get(span.page()-1);
            if (span.start() < 0 || span.end() > page.text().length() || span.start() >= span.end())
                throw new IllegalArgumentException("INVALID_SOURCE_SPAN");
            result.append(page.text(), span.start(), span.end());
        }
        return result.toString();
    }

    private static Set<String> words(String text) {
        Set<String> result = new HashSet<>();
        for (String word : Objects.toString(text, "").toLowerCase(Locale.ROOT).split("[^\\p{L}]+"))
            if (word.length() > 3 && !Set.of("chapter", "module", "lesson", "week", "weeks").contains(word)) result.add(word);
        return result;
    }
}
