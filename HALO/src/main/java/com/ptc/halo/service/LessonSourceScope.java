package com.ptc.halo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptc.halo.entity.*;
import com.ptc.halo.component.ModuleMaterialIndex;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Versioned aggregate persisted with the module/draft and copied into immutable revision history. */
@Service
public class LessonSourceScope {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(LessonSourceScope.class);
    public record Document(@com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) Long fileId,String name,String checksum,ObtlSections.Detection extraction, @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) ExcelObtl.Evidence workbook) {
        public Document(Long fileId,String name,String checksum,ObtlSections.Detection extraction){this(fileId,name,checksum,extraction,null);}
        boolean current(){return workbook!=null ? ExcelObtl.VERSION.equals(workbook.version()) : extraction!=null && ObtlSections.VERSION.equals(extraction.version());}
    }
    public record Correction(String checksum,String extractionVersion,List<ObtlSections.Span> fragments,String displayLabel) {
        public Correction(String checksum,String extractionVersion,List<ObtlSections.Span> fragments){this(checksum,extractionVersion,fragments,null);}
    }
    public enum ExamPurpose { RECORD_ONLY, REVIEW_GUIDE }
    public record Examination(ExamPurpose purpose,List<String> coverageIds) {}
    public record Choice(@com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) Long fileId,List<String> sectionIds,boolean instructional,Correction correction,@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Examination examination) {
        public Choice(Long fileId,List<String> ids,boolean instructional,Correction correction){this(fileId,ids,instructional,correction,null);}
        public Choice(Long fileId,List<String> sectionIds,boolean instructional){this(fileId,sectionIds,instructional,null);}
    }
    private static List<ObtlSections.Span> permitted(Document doc,Choice choice,ObtlSections.Section section){
        var correction=choice.correction();
        if (section.warnings().contains(ObtlSections.MANUAL_REGION)) {
            if(correction==null || choice.sectionIds().size()!=1 || correction.displayLabel()==null
                    || !correction.displayLabel().matches("[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N} .,&():/–—-]{0,99}"))
                throw failure("SOURCE_REGION_REVIEW_REQUIRED");
            if(!doc.checksum().equals(correction.checksum()) || !doc.extraction().version().equals(correction.extractionVersion()))
                throw failure("SOURCE_REEXTRACTION_REQUIRED");
            if(!section.spans().equals(correction.fragments()))throw failure("SOURCE_REGION_MISMATCH");
            for(var span:section.spans()){
                var page=doc.extraction().pages().get(span.page()-1);
                if(page.rows().stream().filter(r->r.gridKey().startsWith("recovery:") && r.start()==span.start()
                        && r.end()==span.end() && r.left()>=0 && r.top()>=0 && r.right()>r.left() && r.bottom()>r.top()).count()!=1)
                    throw failure("SOURCE_REGION_MISMATCH");
            }
            return section.spans();
        }
        if(correction==null)return section.spans();
        var fragments=correction.fragments();
        if(!doc.checksum().equals(correction.checksum()) || !doc.extraction().version().equals(correction.extractionVersion())
                || choice.sectionIds().size()!=1 || fragments==null || fragments.isEmpty() || fragments.size()>256
                || section.id().equals("whole") || section.id().startsWith("ambiguous-") || section.id().startsWith("unruled-"))
            throw failure("INVALID_SOURCE_SELECTION");
        int lastPage=0,lastEnd=0;
        for(var fragment:fragments){
            if(fragment==null || fragment.start()<0 || fragment.end()<=fragment.start()
                    || fragment.page()<lastPage || fragment.page()==lastPage && fragment.start()<lastEnd
                    || section.spans().stream().noneMatch(owner->owner.page()==fragment.page()
                        && owner.start()<=fragment.start() && fragment.end()<=owner.end()))
                throw failure("INVALID_SOURCE_SELECTION");
            String page=doc.extraction().pages().get(fragment.page()-1).text();
            if(page.substring(fragment.start(),fragment.end()).isBlank()
                || Character.isLowSurrogate(page.charAt(fragment.start()))
                || Character.isHighSurrogate(page.charAt(fragment.end()-1)))throw failure("INVALID_SOURCE_SELECTION");
            lastPage=fragment.page();lastEnd=fragment.end();
        }
        return fragments;
    }
    public enum Mode { SOURCE_GROUNDED, OBTL_EXPANSION }
    public record Review(Long actor,String at,String contentHash) {}
    public record Snapshot(int format,String id,Long moduleId,List<Document> documents,List<Choice> choices,
                           Long confirmedBy,String confirmedAt,boolean instructionVerified,Mode mode,String generatedHash,Review review) {
        public Snapshot { mode=mode==null?Mode.SOURCE_GROUNDED:mode; }
        public Snapshot(int f,String id,Long m,List<Document> d,List<Choice> c,Long by,String at,boolean verified){this(f,id,m,d,c,by,at,verified,Mode.SOURCE_GROUNDED,null,null);}
    }
    public record Lesson(String objectives,String knowledge,String examples,String summary) {
        public static Lesson of(AiLearningModuleEntity m){return new Lesson(m.getGeneratedObjectives(),m.getGeneratedKnowledge(),m.getGeneratedExamples(),m.getGeneratedSummary());}
        public void validate(){
            for(String value:Arrays.asList(objectives,knowledge,examples,summary))if(value==null||value.isBlank()||value.length()>30000)throw failure("INVALID_EXPANSION_CONTENT");
        }
        public void apply(AiLearningModuleEntity m){
            validate();
            m.setGeneratedObjectives(objectives);m.setGeneratedKnowledge(knowledge);m.setGeneratedExamples(examples);m.setGeneratedSummary(summary);
        }
    }
    private final ObjectMapper json; private final LessonMaterialStorage storage;private final ObtlExtractionProcess extractor;
    public LessonSourceScope(ObjectMapper json,LessonMaterialStorage storage,ObtlExtractionProcess extractor){this.json=json;this.storage=storage;this.extractor=extractor;}
    public Snapshot read(String value){
        try{var result=json.readValue(value,Snapshot.class);if(result.format()<1 || result.format()>5)throw new IllegalArgumentException();return result;}
        catch(Exception e){throw failure("SOURCE_SCOPE_UNAVAILABLE");}
    }
    public String write(Snapshot value){try{return json.writeValueAsString(value);}catch(Exception e){throw failure("SOURCE_SCOPE_UNAVAILABLE");}}
    public String extract(AiLearningModuleEntity module){
        if(module.getFiles().isEmpty()||module.getFiles().size()>10)throw failure("MODULE_MATERIALS_REQUIRED");
        List<Document> documents=new ArrayList<>();
        Snapshot old=module.getSourceScope()==null?null:read(module.getSourceScope());
        for(var file:module.getFiles()){
            boolean excel=ExcelObtl.MIME.equals(file.getFileType());
            if(!excel && !"application/pdf".equals(file.getFileType()))throw failure("SCOPED_SOURCE_PDF_REQUIRED");
            String stage="STORAGE_READ";
            try{
                byte[] bytes=storage.read(file.getFilePath());String checksum=hash(bytes);
                stage="SOURCE_PROCESSING";
                Document existing=old==null?null:old.documents().stream().filter(d->Objects.equals(d.fileId(),file.getId())&&d.checksum().equals(checksum)&&d.current()).findFirst().orElse(null);
                if(existing!=null){documents.add(existing);checkTextBudget(documents);continue;}
                if(excel){documents.add(new Document(file.getId(),file.getOriginalFileName(),checksum,null,ExcelObtl.parse(bytes)));checkTextBudget(documents);continue;}
                stage="WORKER";
                var parsed=extractor.extract(bytes);
                stage="SOURCE_PROCESSING";
                var detection=ObtlSections.detect(parsed.detection().pages(),module.getWeek().getTitle());
                // A single-topic supplement without headings has an explicit whole-document proposal.
                if(detection.sections().isEmpty()){
                    List<ObtlSections.Span> spans=detection.pages().stream().filter(p->!p.text().isEmpty()).map(p->new ObtlSections.Span(p.number(),0,p.text().length())).toList();
                    detection=new ObtlSections.Detection(detection.version(),detection.pages(),List.of(new ObtlSections.Section("whole","Whole document — verify single-topic scope",spans,List.of("PROFESSOR_BOUNDARY_REVIEW_REQUIRED"),0)),detection.warnings());
                }
                documents.add(new Document(file.getId(),file.getOriginalFileName(),checksum,detection));
                checkTextBudget(documents);
            }catch(ResponseStatusException e){throw e;}catch(Exception e){
                String reason=stage; Integer status=null,exit=null; String operation="NONE";
                if(e instanceof SupabaseLessonMaterialStorage.HttpFailure http){
                    status=http.status();operation=http.operation();
                    reason=status==401||status==403?"STORAGE_AUTHORIZATION":"STORAGE_HTTP_FAILURE";
                }else if(e instanceof SupabaseLessonMaterialStorage.MissingResource missing){reason="STORAGE_NOT_FOUND";status=404;operation=missing.operation();}
                else if(e instanceof java.io.FileNotFoundException){reason="STORAGE_NOT_FOUND";}
                else if(e instanceof ObtlExtractionProcess.Failure worker){reason=worker.reason().name();exit=worker.exitCode();}
                log.warn("obtl_extraction_failure stage={} reason={} exceptionType={} storageOperation={} httpStatus={} workerExit={}",
                    stage,reason,e.getClass().getSimpleName(),operation,status,exit);
                throw failure("SOURCE_EXTRACTION_FAILED");
            }
        }
        return write(new Snapshot(documents.stream().anyMatch(d->d.workbook()!=null)?4:1,UUID.randomUUID().toString(),module.getId(),List.copyOf(documents),List.of(),null,null,false));
    }
    private void checkTextBudget(List<Document> documents){
        if(documents.stream().anyMatch(d->d.workbook()!=null) && write(new Snapshot(4,"budget",0L,documents,List.of(),null,null,false)).length()>ObtlSections.MAX_CHARS)throw failure("SOURCE_TEXT_LIMIT");
        if(documents.stream().filter(d->d.extraction()!=null).flatMap(d->d.extraction().pages().stream()).mapToLong(p->p.text().length()).sum()>ObtlSections.MAX_CHARS)
            throw failure("SOURCE_TEXT_LIMIT");
    }
    public String confirm(AiLearningModuleEntity module,List<Choice> choices,Long actor){
        return confirm(module,choices,actor,Mode.SOURCE_GROUNDED);
    }
    public String confirm(AiLearningModuleEntity module,List<Choice> choices,Long actor,Mode mode){
        Snapshot snapshot=read(module.getSourceScope());validateDocuments(module,snapshot);
        if(choices==null||choices.isEmpty()||choices.size()>10)throw failure("SOURCE_SELECTION_REQUIRED");
        Set<Long> seen=new HashSet<>();
        for(var choice:choices){
            if(choice==null||!seen.add(choice.fileId())||choice.sectionIds()==null||choice.sectionIds().isEmpty()||choice.sectionIds().size()>256||new HashSet<>(choice.sectionIds()).size()!=choice.sectionIds().size())throw failure("INVALID_SOURCE_SELECTION");
            var document=snapshot.documents().stream().filter(d->Objects.equals(d.fileId(),choice.fileId())).findFirst().orElseThrow(()->failure("INVALID_SOURCE_SELECTION"));
            if(!document.current())throw failure("SOURCE_REEXTRACTION_REQUIRED");
            validateExamination(choices,choice,document);
            if(document.workbook()!=null){
                var selected=document.workbook().sections().stream().filter(s->choice.sectionIds().contains(s.id())).toList();
                if(choice.correction()!=null || selected.size()!=choice.sectionIds().size())throw failure("INVALID_SOURCE_SELECTION");
                if(selected.stream().anyMatch(s->!s.complete()))throw failure("SOURCE_BOUNDARY_REVIEW_REQUIRED");
                continue;
            }
            if(!document.extraction().sections().stream().map(ObtlSections.Section::id).toList().containsAll(choice.sectionIds()))throw failure("INVALID_SOURCE_SELECTION");
            if(document.extraction().sections().stream().filter(s->choice.sectionIds().contains(s.id())).anyMatch(s->s.spans().isEmpty()||s.warnings().contains("AMBIGUOUS_TABLE_BOUNDARY")||s.warnings().contains("TABLE_CONTINUATION_UNCONFIRMED")))throw failure("SOURCE_BOUNDARY_REVIEW_REQUIRED");
            document.extraction().sections().stream().filter(s->choice.sectionIds().contains(s.id())).forEach(s->permitted(document,choice,s));
        }
        int format=choices.stream().anyMatch(c->c.examination()!=null)?5:snapshot.documents().stream().anyMatch(d->d.workbook()!=null)?4:choices.stream().anyMatch(c->c.sectionIds().stream().anyMatch(id->id.startsWith("region-p")))?3:
                choices.stream().anyMatch(choice->choice.correction()!=null)?2:1;
        return write(new Snapshot(format,UUID.randomUUID().toString(),module.getId(),snapshot.documents(),List.copyOf(choices),actor,java.time.Instant.now().toString(),false,mode,null,null));
    }
    private static void validateExamination(List<Choice> choices,Choice choice,Document doc){
        boolean selectedExam=doc.workbook()!=null && doc.workbook().sections().stream()
            .anyMatch(s->choice.sectionIds().contains(s.id()) && "EXAMINATION".equals(s.type()));
        Examination exam=choice.examination();
        if(!selectedExam){if(exam!=null)throw failure("INVALID_SOURCE_SELECTION");return;}
        if(exam==null || choices.size()!=1 || choice.sectionIds().size()!=1 || exam.purpose()==null || exam.coverageIds()==null)
            throw failure("EXAM_COVERAGE_REQUIRED");
        var ids=exam.coverageIds();
        if(exam.purpose()==ExamPurpose.RECORD_ONLY){
            if(!ids.isEmpty() || choice.instructional())throw failure("INVALID_SOURCE_SELECTION");
            return;
        }
        if(ids.isEmpty() || ids.size()>256 || new HashSet<>(ids).size()!=ids.size())throw failure("EXAM_COVERAGE_REQUIRED");
        long matches=doc.workbook().sections().stream().filter(s->ids.contains(s.id()) && s.complete() && !"EXAMINATION".equals(s.type())).count();
        if(matches!=ids.size())throw failure("INVALID_SOURCE_SELECTION");
    }
    public boolean examinationGuide(AiLearningModuleEntity module){
        return read(module.getSourceScope()).choices().stream().anyMatch(c->c.examination()!=null && c.examination().purpose()==ExamPurpose.REVIEW_GUIDE);
    }
    public boolean expansion(AiLearningModuleEntity module){return module.getSourceScope()!=null&&read(module.getSourceScope()).mode()==Mode.OBTL_EXPANSION;}
    public static boolean isExpansion(String snapshot){
        if(snapshot==null)return false;
        try{return "OBTL_EXPANSION".equals(new ObjectMapper().readTree(snapshot).path("mode").asText());}catch(Exception e){throw failure("SOURCE_SCOPE_UNAVAILABLE");}
    }
    public boolean outlineOnly(AiLearningModuleEntity module){return read(module.getSourceScope()).choices().stream().noneMatch(Choice::instructional);}
    public void requirePublishedReady(AiLearningModuleEntity module){
        if(module.getSourceScope()==null)return;
        requireCurrentExtraction(module);
        requirePublishedReadable(module);
    }
    public void requirePublishedReadable(AiLearningModuleEntity module){
        if(module.getSourceScope()==null)return;
        var s=read(module.getSourceScope());validateDocuments(module,s);
        if(s.confirmedBy()==null||!s.instructionVerified())throw failure(s.mode()==Mode.OBTL_EXPANSION
            ? "EXPANSION_GENERATION_REQUIRED" : "INSTRUCTIONAL_SOURCES_REQUIRED");
        requireExpansionReview(module);
    }
    public void requireExpansionReview(AiLearningModuleEntity module){
        if(!expansion(module))return;
        var s=read(module.getSourceScope());
        if(!s.instructionVerified()||s.review()==null||!contentHash(module).equals(s.review().contentHash())||!contentHash(module).equals(s.generatedHash()))throw failure("EXPANSION_REVIEW_REQUIRED");
    }
    private String contentHash(AiLearningModuleEntity module){try{return hash(json.writeValueAsBytes(Lesson.of(module)));}catch(Exception e){throw failure("INVALID_EXPANSION_CONTENT");}}
    public void acknowledgeExpansion(AiLearningModuleEntity module,Long actor){
        var s=read(module.getSourceScope());
        if(!expansion(module)||!s.instructionVerified()||!contentHash(module).equals(s.generatedHash()))throw failure("EXPANSION_REVIEW_REQUIRED");
        module.setSourceScope(write(new Snapshot(s.format(),UUID.randomUUID().toString(),s.moduleId(),s.documents(),s.choices(),s.confirmedBy(),s.confirmedAt(),true,s.mode(),s.generatedHash(),new Review(actor,java.time.Instant.now().toString(),s.generatedHash()))));
    }
    public void requireCurrentExtraction(AiLearningModuleEntity module){
        var snapshot=read(module.getSourceScope());
        for(var choice:snapshot.choices()){
            var doc=snapshot.documents().stream().filter(d->Objects.equals(d.fileId(),choice.fileId())).findFirst().orElseThrow(()->failure("INVALID_SOURCE_SELECTION"));
            validateExamination(snapshot.choices(),choice,doc);
            if(choice.examination()!=null && choice.examination().purpose()==ExamPurpose.RECORD_ONLY)throw failure("EXAM_RECORD_ONLY");
        }
        if(snapshot.documents().stream().anyMatch(d->!d.current()))
            throw failure("SOURCE_REEXTRACTION_REQUIRED");
    }
    public void markInstructionVerified(AiLearningModuleEntity module){
        var s=read(module.getSourceScope());module.setSourceScope(write(new Snapshot(s.format(),s.id(),s.moduleId(),s.documents(),s.choices(),s.confirmedBy(),s.confirmedAt(),true,s.mode(),expansion(module)?contentHash(module):null,null)));
    }
    /** Internal revision fingerprints may be built before acknowledgment; Student reads may not. */
    public ModuleMaterialIndex.Index teachingIndex(AiLearningModuleEntity module,boolean reviewed){
        if(!expansion(module))return index(module,true);
        var curriculum=index(module,false);if(reviewed)requireExpansionReview(module);
        var lesson=Lesson.of(module);lesson.validate();
        String text="AI-generated elaboration; Professor-reviewed on publication. Not extracted PDF facts.\nObjectives: "+lesson.objectives()+"\nInstruction: "+lesson.knowledge()+"\nExamples and scenarios: "+lesson.examples()+"\nSummary: "+lesson.summary();
        String identity=read(module.getSourceScope()).id()+":"+contentHash(module);
        List<ModuleMaterialIndex.Chunk> chunks=new ArrayList<>();
        for(int start=0;start<text.length();start+=2200)chunks.add(new ModuleMaterialIndex.Chunk(identity+":lesson:"+start,null,"Professor-approved AI-generated lesson",0,text.substring(start,Math.min(start+2200,text.length())),module.getWeek().getTitle()));
        return new ModuleMaterialIndex.Index(module.getId(),curriculum.fingerprint()+":"+identity,List.copyOf(chunks));
    }
    public ModuleMaterialIndex.Index index(AiLearningModuleEntity module,boolean instructionalOnly){
        var s=read(module.getSourceScope());validateDocuments(module,s);
        if(s.confirmedBy()==null)throw failure("SOURCE_CONFIRMATION_REQUIRED");
        List<ModuleMaterialIndex.Chunk> chunks=new ArrayList<>();
        for(var choice:s.choices()){
            if(instructionalOnly&&!choice.instructional())continue;
            var doc=s.documents().stream().filter(d->Objects.equals(d.fileId(),choice.fileId())).findFirst().orElseThrow();
            if(doc.workbook()!=null){
                String[] headers=ExcelObtl.CONTENT.split("\\|");
                var ids=choice.sectionIds();
                if(choice.examination()!=null){
                    validateExamination(s.choices(),choice,doc);
                    if(choice.examination().purpose()==ExamPurpose.RECORD_ONLY)throw failure("EXAM_RECORD_ONLY");
                    ids=choice.examination().coverageIds();
                }
                for(var section:doc.workbook().sections())if(ids.contains(section.id())){
                    for(var row:section.content())for(int col=2;col<=9;col++){
                        String value=row.at(col);
                        for(int start=0;start<value.length();start+=2200){
                            String id=s.id()+":"+doc.fileId()+":"+section.id()+":"+row.row()+":"+col+":"+start;
                            chunks.add(new ModuleMaterialIndex.Chunk(id,doc.fileId(),doc.name()+" CONTENT row "+row.row(),0,
                                headers[col]+": "+value.substring(start,Math.min(start+2200,value.length())),section.title()));
                            if(chunks.size()>256)throw failure("SOURCE_TEXT_LIMIT");
                        }
                    }
                }
                continue;
            }
            for(var section:doc.extraction().sections())if(choice.sectionIds().contains(section.id())){
                for(var span:permitted(doc,choice,section)){
                    String page=doc.extraction().pages().get(span.page()-1).text();
                    for(int start=span.start();start<span.end();start+=2200){
                        String text=page.substring(start,Math.min(start+2200,span.end()));
                        String id=s.id()+":"+doc.fileId()+":"+span.page()+":"+start;
                        chunks.add(new ModuleMaterialIndex.Chunk(id,doc.fileId(),doc.name(),span.page(),text,section.heading()));
                        if(chunks.size()>256)throw failure("SOURCE_TEXT_LIMIT");
                    }
                }
            }
        }
        return new ModuleMaterialIndex.Index(module.getId(),s.id()+":"+hash(module.getSourceScope().getBytes(java.nio.charset.StandardCharsets.UTF_8)),List.copyOf(chunks));
    }
    public byte[] originalForReview(AiLearningModuleEntity module,Long fileId){
        var snapshot=read(module.getSourceScope());
        if(!Objects.equals(snapshot.moduleId(),module.getId()))throw failure("MODULE_MATERIAL_MISMATCH");
        var doc=snapshot.documents().stream().filter(d->Objects.equals(d.fileId(),fileId)).findFirst().orElseThrow(()->failure("INVALID_SOURCE_SELECTION"));
        if(doc.workbook()!=null)throw failure("INVALID_SOURCE_SELECTION");
        var file=module.getFiles().stream().filter(f->Objects.equals(f.getId(),fileId)).findFirst().orElseThrow(()->failure("SOURCE_REEXTRACTION_REQUIRED"));
        try{
            byte[] bytes=storage.read(file.getFilePath());
            if(!hash(bytes).equals(doc.checksum()))throw failure("SOURCE_REEXTRACTION_REQUIRED");
            return bytes;
        }catch(java.io.IOException e){throw failure("SOURCE_SCOPE_UNAVAILABLE");}
    }
    public void validateDocuments(AiLearningModuleEntity module,Snapshot snapshot){
        if(!Objects.equals(module.getId(),snapshot.moduleId()))throw failure("MODULE_MATERIAL_MISMATCH");
        if(snapshot.documents().size()!=module.getFiles().size())throw failure("SOURCE_REEXTRACTION_REQUIRED");
        // Attachment row IDs can change when publishing a revision; content identity cannot.
        List<String> actual=new ArrayList<>();
        for(var file:module.getFiles())try{actual.add(file.getOriginalFileName()+":"+hash(storage.read(file.getFilePath())));}
            catch(java.io.IOException e){throw failure("SOURCE_SCOPE_UNAVAILABLE");}
        List<String> expected=new ArrayList<>(snapshot.documents().stream().map(d->d.name()+":"+d.checksum()).toList());
        Collections.sort(actual);Collections.sort(expected);
        if(!actual.equals(expected))throw failure("SOURCE_REEXTRACTION_REQUIRED");
    }
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
    private static ResponseStatusException failure(String code){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,code);}
}
