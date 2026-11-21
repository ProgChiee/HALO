package com.ptc.halo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptc.halo.service.*;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.component.ModuleMaterialIndex;
import com.ptc.halo.repository.AiLearningModuleRepository;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LessonSourceScopeTest {
    final ObjectMapper json=new ObjectMapper();
    final LessonMaterialStorage storage=mock(LessonMaterialStorage.class);
    final ObtlExtractionProcess worker=mock(ObtlExtractionProcess.class);
    final LessonSourceScope scope=new LessonSourceScope(json,storage,worker);
    final AiLearningModuleEntity module=new AiLearningModuleEntity();
    final ChatClient chat=mock(ChatClient.class,RETURNS_DEEP_STUBS);
    AiGenerationService generation;
    @BeforeEach void setup()throws Exception {
        module.setId(7L);module.setStatus(LessonStatus.PENDING);
        var subject=new SubjectEntity();subject.setSubjectName("Hospitality");
        var week=new WeekEntity();week.setTitle("Management");week.setSubject(subject);module.setWeek(week);
        StringBuilder text=new StringBuilder();for(int i=1;i<=11;i++)text.append("Chapter ").append(i).append(" Management\nEvidence-").append(i).append("\n");
        var detection=ObtlSections.detect(List.of(new ObtlSections.Page(1,text.toString())),"Management");
        byte[] bytes="synthetic original".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(storage.read("original")).thenReturn(bytes);
        when(worker.extract(bytes)).thenReturn(new ObtlPdfText.Extracted("unused",detection));
        var file=new AiLearningFileEntity();file.setId(3L);file.setFilePath("original");file.setOriginalFileName("OBTL.pdf");file.setFileType("application/pdf");module.addFile(file);
        module.setSourceScope(scope.extract(module));
        var builder=mock(ChatClient.Builder.class);when(builder.build()).thenReturn(chat);
        generation=new AiGenerationService(builder,mock(AiLearningModuleRepository.class),json,mock(ModuleMaterialIndex.class),mock(ModuleGenerationState.class),storage);
        ReflectionTestUtils.setField(generation,"sourceScopes",scope);
    }
    @Test void standardExcelDraftIsScopedBySectionAndContentRow() throws Exception {
        String path=System.getenv("HALO_OBTL_FIXTURE");
        Assumptions.assumeTrue(path!=null,"Set HALO_OBTL_FIXTURE to the Art Appreciation workbook");
        byte[] bytes=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path));
        var upload=new FileUploadService(storage).validate(new org.springframework.mock.web.MockMultipartFile("files","course.xlsx",ExcelObtl.MIME,bytes));
        var file=module.getFiles().get(0);file.setFileType(upload.mediaType());file.setOriginalFileName(upload.originalName());
        when(storage.read("original")).thenReturn(bytes);
        module.setSourceScope(scope.extract(module));
        var draft=scope.read(module.getSourceScope());var workbook=draft.documents().get(0).workbook();
        assertEquals(4,draft.format());assertNull(draft.confirmedBy());
        assertEquals("Art Appreciation",workbook.course().get("Course Title *"));
        assertEquals(8,workbook.pilo().size());assertEquals(9,workbook.cilo().size());assertEquals(17,workbook.sections().size());
        for(int i:List.of(7,16))assertEquals("EXAMINATION",workbook.sections().get(i).type());
        for(var section:workbook.sections())for(var row:section.content())assertEquals(section.id(),row.cells().get(0));
        assertThrows(ResponseStatusException.class,()->scope.index(module,false));
        var first=workbook.sections().get(0);
        var choices=List.of(new LessonSourceScope.Choice(3L,List.of(first.id()),false));
        assertThrows(ResponseStatusException.class,()->scope.confirm(module,List.of(new LessonSourceScope.Choice(3L,List.of("invalid"),false)),5L));
        module.setSourceScope(scope.confirm(module,choices,5L,LessonSourceScope.Mode.OBTL_EXPANSION));
        var chunks=scope.index(module,false).chunks();assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(c->c.id().contains(":"+first.id()+":")));
        assertTrue(chunks.stream().anyMatch(c->c.text().contains(first.content().get(0).cells().get(2))));
        assertTrue(scope.index(module,true).chunks().isEmpty());
        assertThrows(ResponseStatusException.class,()->scope.requirePublishedReady(module));
        when(storage.read("original")).thenReturn(new byte[]{0});
        assertThrows(ResponseStatusException.class,()->scope.confirm(module,choices,5L));
    }
    @Test void abttMidtermCoverageIsExplicitAndExclusive() throws Exception {
        String path=System.getenv("HALO_ABTT_XLSX");Assumptions.assumeTrue(path!=null);
        byte[] bytes=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path));
        var file=module.getFiles().get(0);file.setFileType(ExcelObtl.MIME);file.setOriginalFileName("ABTT.xlsx");
        when(storage.read("original")).thenReturn(bytes);
        module.setSourceScope(scope.extract(module));
        var exam=List.of("SEC-007");
        assertThrows(ResponseStatusException.class,()->scope.confirm(module,List.of(new LessonSourceScope.Choice(3L,exam,false)),5L));
        var record=new LessonSourceScope.Examination(LessonSourceScope.ExamPurpose.RECORD_ONLY,List.of());
        var mode=LessonSourceScope.Mode.OBTL_EXPANSION;
        module.setSourceScope(scope.confirm(module,List.of(new LessonSourceScope.Choice(3L,exam,false,null,record)),5L,mode));
        assertEquals("EXAM_RECORD_ONLY",assertThrows(ResponseStatusException.class,()->generation.generateRevision(module)).getReason());
        for(var invalid:List.of(List.<String>of(),List.of("unknown"),List.of("SEC-007"),List.of("SEC-002","SEC-002"))){
            var plan=new LessonSourceScope.Examination(LessonSourceScope.ExamPurpose.REVIEW_GUIDE,invalid);
            assertThrows(ResponseStatusException.class,()->scope.confirm(module,List.of(new LessonSourceScope.Choice(3L,exam,false,null,plan)),5L,mode));
        }
        var ids=List.of("SEC-002","SEC-003","SEC-004","SEC-005","SEC-006");
        var plan=new LessonSourceScope.Examination(LessonSourceScope.ExamPurpose.REVIEW_GUIDE,ids);
        module.setSourceScope(scope.confirm(module,List.of(new LessonSourceScope.Choice(3L,exam,false,null,plan)),5L,mode));
        var snapshot=scope.read(module.getSourceScope());assertEquals(5,snapshot.format());
        assertEquals(snapshot,scope.read(scope.write(snapshot)));
        var chunks=scope.index(module,false).chunks();
        assertEquals(new HashSet<>(ids),chunks.stream().map(c->c.id().split(":")[2]).collect(java.util.stream.Collectors.toSet()));
        var spec=mock(ChatClient.ChatClientRequestSpec.class,RETURNS_DEEP_STUBS);
        when(chat.prompt().system(startsWith("Order the supplied"))).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call().content()).thenReturn(json.writeValueAsString(Map.of("sourceIds",chunks.stream().map(ModuleMaterialIndex.Chunk::id).toList(),"knowledge","invented technical content")));
        generation.generateRevision(module);assertFalse(module.getGeneratedKnowledge().contains("invented technical content"));
        assertThrows(ResponseStatusException.class,()->scope.requirePublishedReady(module));
        scope.acknowledgeExpansion(module,5L);
        assertTrue(scope.teachingIndex(module,true).chunks().stream().allMatch(c->c.fileId()==null));
        when(storage.read("original")).thenReturn(new byte[]{1});
        assertThrows(ResponseStatusException.class,()->scope.index(module,false));
    }
    void confirm(boolean instructional){
        var section=scope.read(module.getSourceScope()).documents().get(0).extraction().sections().get(0);
        module.setSourceScope(scope.confirm(module,List.of(new LessonSourceScope.Choice(3L,List.of(section.id()),instructional)),5L));
    }
    @Test void manualRegionRequiresReviewAndIndexesOnlyItsOriginalEvidence()throws Exception {
        var old=scope.read(module.getSourceScope());
        var doc=old.documents().get(0);
        String evidence="Shared wording\n", grid="recovery:topic|10|20|[10,20,300]";
        var page1=new ObtlSections.Page(1,evidence.repeat(2),List.of(
                new ObtlSections.Row(0,evidence.length(),"Chapter 1",false,10,10,300,20,grid),
                new ObtlSections.Row(evidence.length(),2*evidence.length(),"Chapter 2",false,10,20,300,30,grid)),true);
        var page2=new ObtlSections.Page(2,"Continued work",List.of(
                new ObtlSections.Row(0,14,"Chapter 2",true,10,10,300,20,grid)),true);
        var detection=ObtlSections.detect(List.of(page1,page2),"");
        var target=detection.sections().stream().filter(s->s.heading().equals("Chapter 2")).findFirst().orElseThrow();
        assertEquals(2,target.spans().size());
        var document=new LessonSourceScope.Document(doc.fileId(),doc.name(),doc.checksum(),detection);
        module.setSourceScope(scope.write(new LessonSourceScope.Snapshot(1,"draft",module.getId(),List.of(document),List.of(),null,null,false)));
        var ids=List.of(target.id());
        assertEquals("SOURCE_REGION_REVIEW_REQUIRED",assertThrows(ResponseStatusException.class,
            ()->scope.confirm(module,List.of(new LessonSourceScope.Choice(doc.fileId(),ids,false)),5L)).getReason());
        for(var spans:List.of(List.of(target.spans().get(0)),List.of(target.spans().get(0),target.spans().get(0)),
                List.of(new ObtlSections.Span(1,0,evidence.length()),target.spans().get(1)))){
            var forged=new LessonSourceScope.Correction(doc.checksum(),detection.version(),spans,"Week 3");
            assertEquals("SOURCE_REGION_MISMATCH",assertThrows(ResponseStatusException.class,()->scope.confirm(module,
                List.of(new LessonSourceScope.Choice(doc.fileId(),ids,false,forged)),5L)).getReason());
        }
        var review=new LessonSourceScope.Correction(doc.checksum(),detection.version(),target.spans(),"Week 3");
        module.setSourceScope(scope.confirm(module,List.of(new LessonSourceScope.Choice(doc.fileId(),ids,false,review)),5L,LessonSourceScope.Mode.OBTL_EXPANSION));
        assertEquals(3,scope.read(module.getSourceScope()).format());
        assertEquals(List.of(evidence,"Continued work"),scope.index(module,false).chunks().stream().map(ModuleMaterialIndex.Chunk::text).toList());
        assertTrue(scope.index(module,true).chunks().isEmpty());
    }
    @Test void correctedEvidenceIsValidatedAndIsTheOnlyTextIndexed()throws Exception{
        var d=scope.read(module.getSourceScope()).documents().get(0);
        var section=d.extraction().sections().get(0);
        int start=d.extraction().pages().get(0).text().indexOf("Evidence-1");
        var fragment=new ObtlSections.Span(1,start,start+10);
        var correction=new LessonSourceScope.Correction(d.checksum(),d.extraction().version(),List.of(fragment));
        var choice=new LessonSourceScope.Choice(d.fileId(),List.of(section.id()),false,correction);
        module.setSourceScope(scope.confirm(module,List.of(choice),5L,LessonSourceScope.Mode.OBTL_EXPANSION));
        assertEquals("Evidence-1",scope.index(module,false).chunks().get(0).text());
        assertEquals(correction,scope.read(module.getSourceScope()).choices().get(0).correction());
        assertEquals(2,scope.read(module.getSourceScope()).format());
        for(var invalid:List.of(new LessonSourceScope.Correction("stale",d.extraction().version(),List.of(fragment)),
                new LessonSourceScope.Correction(d.checksum(),d.extraction().version(),List.of(fragment,fragment)),
                new LessonSourceScope.Correction(d.checksum(),d.extraction().version(),d.extraction().sections().get(1).spans()))){
            var bad=new LessonSourceScope.Choice(d.fileId(),List.of(section.id()),true,invalid);
            assertThrows(ResponseStatusException.class,()->scope.confirm(module,List.of(bad),5L));
        }
    }
    @Test void ungeneratedExpansionDoesNotRequestSupplementaryInstruction() {
        expansion();
        assertEquals("EXPANSION_GENERATION_REQUIRED", assertThrows(ResponseStatusException.class,
            () -> scope.requirePublishedReady(module)).getReason());
        assertFalse(scope.read(module.getSourceScope()).instructionVerified());
        confirm(false);
        assertEquals("INSTRUCTIONAL_SOURCES_REQUIRED", assertThrows(ResponseStatusException.class,
            () -> scope.requirePublishedReady(module)).getReason());
    }
    @Test void cateringWeekCanBeConfirmedAndReloadedAsAnOutlineWithoutOtherRows() throws Exception {
        byte[] pdf=ObtlMultiFormatTest.fixture("AHRT-CATERING SERVICE MANAGEMENT_OBTL.pdf",
                "3b07c819d174012bfe8375c3d13966f2404b25554c75d12528c857cac1311e5d");
        when(storage.read("original")).thenReturn(pdf);
        when(worker.extract(pdf)).thenReturn(ObtlPdfText.extract(pdf,""));
        module.setSourceScope(scope.extract(module));
        var doc=scope.read(module.getSourceScope()).documents().get(0);
        var row=ObtlTableBoundaryTest.section(doc.extraction(),"Week 5 & 6");
        var review=new LessonSourceScope.Correction(doc.checksum(),doc.extraction().version(),row.spans(),"Weeks 5–6");
        module.setSourceScope(scope.confirm(module,List.of(new LessonSourceScope.Choice(doc.fileId(),List.of(row.id()),false,review)),
                5L,LessonSourceScope.Mode.OBTL_EXPANSION));
        String allowed=ObtlSections.text(doc.extraction(),row);
        assertEquals(allowed,scope.index(module,false).chunks().stream().map(ModuleMaterialIndex.Chunk::text).collect(java.util.stream.Collectors.joining()));
        assertTrue(scope.outlineOnly(module));
        assertThrows(ResponseStatusException.class,()->scope.requirePublishedReady(module));
        when(storage.read("original")).thenReturn(new byte[]{1,2,3});
        assertEquals("SOURCE_REEXTRACTION_REQUIRED",assertThrows(ResponseStatusException.class,()->scope.index(module,false)).getReason());
    }
    @Test void legacyOutlineRequiresExplicitModeChangeThenInitialGenerationAcceptsOutline() throws Exception {
        confirm(false);
        generation.generatePrepared(module.getId(), module);
        assertEquals(AiGenerationStatus.PENDING, module.getAiGenerationStatus());
        assertFalse(scope.expansion(module));
        verifyNoInteractions(chat);
        expansion();
        module.setSourceScope(scope.write(scope.read(module.getSourceScope())));
        expansionProvider(true);
        generation.generatePrepared(module.getId(), module);
        assertEquals(AiGenerationStatus.COMPLETED, module.getAiGenerationStatus());
        assertTrue(scope.read(module.getSourceScope()).instructionVerified());
        assertEquals("EXPANSION_REVIEW_REQUIRED", assertThrows(ResponseStatusException.class,
            () -> scope.requirePublishedReady(module)).getReason());
    }
    void expansion() {
        var section=scope.read(module.getSourceScope()).documents().get(0).extraction().sections().get(0);
        module.setSourceScope(scope.confirm(module,List.of(new LessonSourceScope.Choice(3L,List.of(section.id()),false)),5L,LessonSourceScope.Mode.OBTL_EXPANSION));
    }
    ChatClient.ChatClientRequestSpec expansionProvider(boolean inScope)throws Exception {
        String id=scope.index(module,false).chunks().get(0).id();
        var spec=mock(ChatClient.ChatClientRequestSpec.class,RETURNS_DEEP_STUBS);
        when(chat.prompt().system(startsWith("Expand only"))).thenReturn(spec);when(spec.user(anyString())).thenReturn(spec);
        when(spec.call().content()).thenReturn(json.writeValueAsString(Map.of("valid",true,"objectives","Explain selected concepts","knowledge","A detailed educational explanation of the selected management concepts.","examples","A hotel manager applies the selected concepts in a practical scenario.","summary","Summary of the selected concepts","sourceIds",List.of(id))));
        when(chat.prompt().system(startsWith("Validate OBTL expansion")).user(anyString()).call().content()).thenReturn("{\"inScope\":"+inScope+",\"complete\":true}");
        return spec;
    }
    @Test void actualFullObtlAloneCanExpandButPublicationRequiresExactContentReview()throws Exception{
        var d=ObtlTableBoundaryTest.original();when(worker.extract(any())).thenReturn(new ObtlPdfText.Extracted("unused",d));
        module.getWeek().setTitle("Cuisines of the Middle East");module.setSourceScope(null);module.setSourceScope(scope.extract(module));
        String section=ObtlTableBoundaryTest.section(d,"Chapter 1:").id();
        module.setSourceScope(scope.confirm(module,List.of(new LessonSourceScope.Choice(3L,List.of(section),false)),5L,LessonSourceScope.Mode.OBTL_EXPANSION));
        var spec=expansionProvider(true);generation.generateRevision(module);
        assertEquals(1,module.getFiles().size());assertEquals(AiGenerationStatus.COMPLETED,module.getAiGenerationStatus());assertNotNull(module.getGeneratedExamples());
        assertEquals("EXPANSION_REVIEW_REQUIRED",assertThrows(ResponseStatusException.class,()->scope.requirePublishedReady(module)).getReason());
        assertThrows(ResponseStatusException.class,()->scope.teachingIndex(module,true));
        var input=org.mockito.ArgumentCaptor.forClass(String.class);verify(spec).user(input.capture());
        assertTrue(input.getValue().contains("Middle East"));assertFalse(input.getValue().contains("Italian culinary heritage"));assertFalse(input.getValue().contains("Classical French"));verify(spec,never()).user(any(java.util.function.Consumer.class));
        String oldIdentity=scope.read(module.getSourceScope()).id();scope.acknowledgeExpansion(module,5L);scope.requirePublishedReady(module);
        assertNotEquals(oldIdentity,scope.read(module.getSourceScope()).id());
        var index=scope.teachingIndex(module,true);assertTrue(index.chunks().stream().allMatch(c->c.fileId()==null&&c.source().contains("AI-generated")));
        assertTrue(index.chunks().get(0).text().contains("detailed educational explanation"));
        assertEquals(scope.read(module.getSourceScope()),scope.read(scope.write(scope.read(module.getSourceScope()))));
        String reviewed=module.getSourceScope();module.setGeneratedKnowledge("Changed explanation");
        assertThrows(ResponseStatusException.class,()->scope.requirePublishedReady(module));assertThrows(ResponseStatusException.class,()->scope.teachingIndex(module,true));
        assertEquals(reviewed,module.getSourceScope());
    }
    @Test void expansionRejectsOutOfScopeAndIncompleteProviderDecisions()throws Exception{
        expansion();expansionProvider(false);
        assertEquals("EXPANSION_SCOPE_INVALID",assertThrows(ResponseStatusException.class,()->generation.generateRevision(module)).getReason());
        assertFalse(scope.read(module.getSourceScope()).instructionVerified());assertThrows(ResponseStatusException.class,()->scope.acknowledgeExpansion(module,5L));
        when(chat.prompt().system(startsWith("Validate OBTL expansion")).user(anyString()).call().content()).thenReturn("{}");
        assertEquals("MATERIAL_RELEVANCE_UNAVAILABLE",assertThrows(ResponseStatusException.class,()->generation.generateRevision(module)).getReason());
    }
    @Test void editedExpansionMustPassValidationAgainAndAssessmentUsesReviewedContent()throws Exception{
        expansion();expansionProvider(true);generation.generateRevision(module);scope.acknowledgeExpansion(module,5L);
        var assessment=mock(AssessmentService.class,CALLS_REAL_METHODS);var ai=mock(AssessmentAiService.class);
        ReflectionTestUtils.setField(assessment,"sourceScopes",scope);ReflectionTestUtils.setField(assessment,"assessmentAiService",ai);
        module.setGeneratedKnowledge("Professor correction within selected concepts");
        assertThrows(ResponseStatusException.class,()->assessment.prepareAssessment(module));verifyNoInteractions(ai);
        generation.validateExpansion(module);assertThrows(ResponseStatusException.class,()->scope.requirePublishedReady(module));scope.acknowledgeExpansion(module,5L);
        var stop=new IllegalStateException("Fixture stop");when(ai.generateAssessment(isNull(),anyString(),eq(module.getGeneratedKnowledge()),anyString(),anyString())).thenThrow(stop);
        assertSame(stop,assertThrows(IllegalStateException.class,()->assessment.prepareAssessment(module)));
        verify(ai).generateAssessment(null,module.getGeneratedObjectives(),module.getGeneratedKnowledge(),module.getGeneratedExamples(),module.getGeneratedSummary());
    }
    @Test void expansionProviderFailureAndForeignIdsCannotProducePublishableLesson()throws Exception{
        expansion();var spec=expansionProvider(true);when(spec.call().content()).thenThrow(new IllegalStateException("fixture"));
        assertEquals(AiGenerationStatus.FAILED,generation.generateRevision(module).getAiGenerationStatus());assertThrows(ResponseStatusException.class,()->scope.requirePublishedReady(module));
        var call=spec.call();doReturn("{\"valid\":true,\"sourceIds\":[\"other-chapter\"]}").when(call).content();
        assertEquals(AiGenerationStatus.FAILED,generation.generateRevision(module).getAiGenerationStatus());assertNull(scope.read(module.getSourceScope()).review());
    }
    @Test void realObtlPlusInstructionalSupplementCanGenerateWithoutUnselectedChapters()throws Exception{
        var outline=ObtlTableBoundaryTest.original();
        byte[] obtl=java.nio.file.Files.readAllBytes(ObtlTableBoundaryTest.ORIGINAL),supplement=ObtlSupplementTest.original();
        var instruction=ObtlPdfText.extract(supplement,"Middle East").detection();
        when(storage.read("original")).thenReturn(obtl);when(worker.extract(obtl)).thenReturn(new ObtlPdfText.Extracted("unused",outline));
        var f=new AiLearningFileEntity();f.setId(4L);f.setOriginalFileName("Chapter 1 supplement.pdf");f.setFilePath("supplement");f.setFileType("application/pdf");module.addFile(f);
        when(storage.read("supplement")).thenReturn(supplement);when(worker.extract(supplement)).thenReturn(new ObtlPdfText.Extracted("unused",instruction));
        module.getWeek().setTitle("Cuisines of the Middle East");module.setSourceScope(null);module.setSourceScope(scope.extract(module));
        var choices=List.of(new LessonSourceScope.Choice(3L,List.of(ObtlTableBoundaryTest.section(outline,"Chapter 1:").id()),false),new LessonSourceScope.Choice(4L,List.of(instruction.sections().get(0).id()),true));
        assertThrows(ResponseStatusException.class,()->generation.generateRevision(module));
        module.setSourceScope(scope.confirm(module,choices,5L));
        assertThrows(ResponseStatusException.class,()->scope.requirePublishedReady(module));
        var chunks=scope.index(module,true).chunks();assertTrue(chunks.stream().allMatch(c->c.fileId().equals(4L)));
        when(chat.prompt().system(startsWith("Evaluate only")).user(anyString()).call().content()).thenReturn("{\"relevant\":true,\"sufficient\":true}");
        var spec=mock(ChatClient.ChatClientRequestSpec.class,RETURNS_DEEP_STUBS);when(chat.prompt().system(startsWith("Generate a lesson ONLY"))).thenReturn(spec);when(spec.user(anyString())).thenReturn(spec);
        when(spec.call().content()).thenReturn(json.writeValueAsString(Map.of("valid",true,"objectives","Identify spices","knowledge","Cumin adds a warm earthy flavor.","examples","Cumin in meat dishes","summary","Middle Eastern spices","sourceIds",List.of(chunks.get(0).id()))));
        generation.generateRevision(module);scope.requirePublishedReady(module);
        var input=org.mockito.ArgumentCaptor.forClass(String.class);verify(spec).user(input.capture());
        assertTrue(input.getValue().contains("Cumin"));assertFalse(input.getValue().contains("Italian culinary heritage"));assertFalse(input.getValue().contains("Classical French"));verify(spec,never()).user(any(java.util.function.Consumer.class));
        String saved=module.getSourceScope();
        assertEquals("INVALID_SOURCE_SELECTION",assertThrows(ResponseStatusException.class,()->scope.confirm(module,List.of(choices.get(1),new LessonSourceScope.Choice(3L,List.of("ambiguous-p4"),true)),5L)).getReason());
        assertEquals(saved,module.getSourceScope());
    }
    @Test void realCuisineSelectionIsConfirmedBeforeGenerationAndMentorIndexing()throws Exception{
        var detection=ObtlTableBoundaryTest.original();
        when(worker.extract(any())).thenReturn(new ObtlPdfText.Extracted("unused",detection));
        module.setSourceScope(null);module.setSourceScope(scope.extract(module));
        assertThrows(ResponseStatusException.class,()->scope.index(module,true));
        String section=ObtlTableBoundaryTest.section(detection,"Chapter 4").id();
        module.setSourceScope(scope.confirm(module,List.of(new LessonSourceScope.Choice(3L,List.of(section),false)),5L));
        String text=scope.index(module,false).chunks().stream().map(ModuleMaterialIndex.Chunk::text).collect(java.util.stream.Collectors.joining(" "));
        assertTrue(text.contains("Italian culinary"));assertFalse(text.contains("Classical French"));assertFalse(text.contains("Greek"));
        var result=generation.generateRevision(module);assertTrue(result.getGeneratedKnowledge().contains("Italian culinary"));
        assertFalse(result.getGeneratedKnowledge().contains("Classical French"));verifyNoInteractions(chat);
        assertThrows(ResponseStatusException.class,()->scope.requirePublishedReady(module));
    }
    @Test void ambiguousTableCannotBeConfirmedEvenAsInstructional()throws Exception{
        var detection=ObtlPdfText.extract(ObtlTableBoundaryTest.fixture(false,true,false),"Cuisine").detection();
        when(worker.extract(any())).thenReturn(new ObtlPdfText.Extracted("unused",detection));
        module.setSourceScope(null);module.setSourceScope(scope.extract(module));
        var choice=new LessonSourceScope.Choice(3L,List.of(detection.sections().get(0).id()),true);
        assertEquals("SOURCE_BOUNDARY_REVIEW_REQUIRED",assertThrows(ResponseStatusException.class,()->scope.confirm(module,List.of(choice),5L)).getReason());
        assertEquals("SOURCE_BOUNDARY_REVIEW_REQUIRED",assertThrows(ResponseStatusException.class,()->scope.confirm(module,List.of(choice),5L,LessonSourceScope.Mode.OBTL_EXPANSION)).getReason());
        assertNull(scope.read(module.getSourceScope()).confirmedBy());verifyNoInteractions(chat);
    }
    @Test void realChapterOneOutlineExpansionPersistsWithoutSupplementAndKeepsMentorScoped()throws Exception{
        var detection=ObtlTableBoundaryTest.original();
        when(worker.extract(any())).thenReturn(new ObtlPdfText.Extracted("unused",detection));
        module.getWeek().setTitle("Cuisines of the Middle East");module.setSourceScope(null);module.setSourceScope(scope.extract(module));
        var choice=new LessonSourceScope.Choice(3L,List.of(ObtlTableBoundaryTest.section(detection,"Chapter 1:").id()),false);
        module.setSourceScope(scope.confirm(module,List.of(choice),5L,LessonSourceScope.Mode.OBTL_EXPANSION));
        module.setSourceScope(scope.write(scope.read(module.getSourceScope())));
        assertTrue(scope.expansion(module));assertTrue(scope.outlineOnly(module));assertFalse(scope.read(module.getSourceScope()).instructionVerified());
        String curriculum=scope.index(module,false).chunks().stream().map(ModuleMaterialIndex.Chunk::text).collect(java.util.stream.Collectors.joining(" "));
        assertTrue(curriculum.contains("Historical influences"));
        for(String excluded:List.of("Greek Cuisine","Iberian Cuisine","North African Cuisine","FINAL EXAMINATION"))assertFalse(curriculum.contains(excluded));
        expansionProvider(true);generation.generatePrepared(module.getId(),module);
        assertEquals(AiGenerationStatus.COMPLETED,module.getAiGenerationStatus());
        assertEquals("EXPANSION_REVIEW_REQUIRED",assertThrows(ResponseStatusException.class,()->scope.requirePublishedReady(module)).getReason());
        scope.acknowledgeExpansion(module,5L);scope.requirePublishedReady(module);
        assertFalse(scope.teachingIndex(module,true).chunks().stream().anyMatch(c->c.text().contains("North African Cuisine")));
        String published=module.getSourceScope();
        module.setSourceScope(scope.confirm(module,List.of(choice),5L,LessonSourceScope.Mode.SOURCE_GROUNDED));
        assertEquals("INSTRUCTIONAL_SOURCES_REQUIRED",assertThrows(ResponseStatusException.class,()->scope.requirePublishedReady(module)).getReason());
        assertNotNull(scope.read(published).review());
    }
    @Test void explicitRefreshReextractsOldVersionWithoutMutatingSavedSnapshot()throws Exception{
        confirm(true);scope.markInstructionVerified(module);String saved=module.getSourceScope().replace(ObtlSections.VERSION,"obtl-table-spans-v3");module.setSourceScope(saved);
        scope.requirePublishedReadable(module);
        assertFalse(scope.index(module,true).chunks().isEmpty());
        assertEquals("SOURCE_REEXTRACTION_REQUIRED",assertThrows(ResponseStatusException.class,()->scope.requirePublishedReady(module)).getReason());
        assertEquals("SOURCE_REEXTRACTION_REQUIRED",assertThrows(ResponseStatusException.class,()->generation.generateRevision(module)).getReason());
        verifyNoInteractions(chat);
        assertEquals("SOURCE_REEXTRACTION_REQUIRED",assertThrows(ResponseStatusException.class,()->confirm(true)).getReason());
        module.setSourceScope(scope.extract(module));verify(worker,times(2)).extract(any());
        assertNull(scope.read(module.getSourceScope()).confirmedBy());assertNotNull(scope.read(saved).confirmedBy());
    }
    @Test void elevenChaptersRequireConfirmationAndOnlyConfirmedChapterEntersIndex(){
        assertEquals("SOURCE_CONFIRMATION_REQUIRED",assertThrows(ResponseStatusException.class,()->scope.index(module,false)).getReason());
        confirm(true);var index=scope.index(module,true);assertEquals(1,index.chunks().size());
        assertTrue(index.chunks().get(0).text().contains("Evidence-1"));assertFalse(index.chunks().get(0).text().contains("Chapter 2"));
        assertThrows(ResponseStatusException.class,()->scope.confirm(module,List.of(new LessonSourceScope.Choice(3L,List.of("foreign"),true)),5L));
        assertThrows(ResponseStatusException.class,()->scope.confirm(module,List.of(new LessonSourceScope.Choice(99L,List.of("foreign"),true)),5L));
    }
    @Test void outlinePlanMakesNoProviderCallAndCannotPublish(){
        confirm(false);var result=generation.generateRevision(module);
        assertEquals(AiGenerationStatus.PENDING,result.getAiGenerationStatus());assertTrue(result.getGeneratedKnowledge().contains("Evidence-1"));
        assertFalse(result.getGeneratedKnowledge().contains("Chapter 2"));verifyNoInteractions(chat);
        assertEquals("INSTRUCTIONAL_SOURCES_REQUIRED",assertThrows(ResponseStatusException.class,()->scope.requirePublishedReady(module)).getReason());
    }
    @Test void newPdfCannotFallBackToFullDocumentGeneration(){
        module.setSourceScope(null);
        assertEquals("SOURCE_CONFIRMATION_REQUIRED",assertThrows(ResponseStatusException.class,()->generation.generatePrepared(7L,module)).getReason());
        verifyNoInteractions(chat);
    }
    @Test void scopedAssessmentInputCannotReuseUnselectedLegacyLessonText(){
        var assessments=mock(AssessmentService.class,CALLS_REAL_METHODS);var ai=mock(AssessmentAiService.class);
        ReflectionTestUtils.setField(assessments,"assessmentAiService",ai);
        module.setLessonText("EXCLUDED_LEGACY_TEXT");module.setGeneratedObjectives("Objectives");module.setGeneratedKnowledge("Selected instruction");module.setGeneratedExamples("Example");module.setGeneratedSummary("Summary");
        var stop=new IllegalStateException("Synthetic provider stop");
        when(ai.generateAssessment(isNull(),eq("Objectives"),eq("Selected instruction"),eq("Example"),eq("Summary"))).thenThrow(stop);
        assertSame(stop,assertThrows(IllegalStateException.class,()->assessments.prepareAssessment(module)));
        verify(ai).generateAssessment(null,"Objectives","Selected instruction","Example","Summary");
    }
    @Test void supplementsReuseExtractionButRequireNewConfirmation()throws Exception{
        confirm(false);String previous=module.getSourceScope();
        var f=new AiLearningFileEntity();f.setId(4L);f.setOriginalFileName("Supplement.pdf");f.setFilePath("supplement");f.setFileType("application/pdf");module.addFile(f);
        byte[] bytes="supplement".getBytes();when(storage.read("supplement")).thenReturn(bytes);
        when(worker.extract(bytes)).thenReturn(new ObtlPdfText.Extracted("unused",ObtlSections.detect(List.of(new ObtlSections.Page(1,"Detailed management instruction without headings.")),"Management")));
        assertThrows(ResponseStatusException.class,()->scope.index(module,false));
        module.setSourceScope(scope.extract(module));verify(worker,times(2)).extract(any());
        assertEquals(2,scope.read(module.getSourceScope()).documents().size());assertNull(scope.read(module.getSourceScope()).confirmedBy());
        assertEquals(1,scope.read(previous).documents().size());assertNotNull(scope.read(previous).confirmedBy());
    }
    @Test void documentMutationAndCrossModuleUseFailClosed()throws Exception{
        confirm(true);scope.markInstructionVerified(module);scope.requirePublishedReady(module);
        module.setId(8L);assertThrows(ResponseStatusException.class,()->scope.index(module,true));module.setId(7L);
        when(storage.read("original")).thenReturn("changed".getBytes());
        assertEquals("SOURCE_REEXTRACTION_REQUIRED",assertThrows(ResponseStatusException.class,()->scope.requirePublishedReady(module)).getReason());
    }
    @Test void revisionTemporaryIdsRoundTripAsStringsWithoutJavaScriptPrecisionLoss()throws Exception{
        long id=-8123456789012345678L;module.getFiles().get(0).setId(id);module.setSourceScope(scope.extract(module));
        assertEquals(Long.toString(id),json.readTree(module.getSourceScope()).path("documents").get(0).path("fileId").textValue());
        var section=scope.read(module.getSourceScope()).documents().get(0).extraction().sections().get(0);
        var choice=json.readValue("{\"fileId\":\""+id+"\",\"sectionIds\":[\""+section.id()+"\"],\"instructional\":false}",LessonSourceScope.Choice.class);
        module.setSourceScope(scope.confirm(module,List.of(choice),5L));
        assertEquals(id,scope.read(module.getSourceScope()).choices().get(0).fileId());
        assertTrue(json.readTree(module.getSourceScope()).path("choices").get(0).path("fileId").isTextual());
    }
    @Test void selectionVersionChangesChunkIdsAndPublicationRowIdsDoNotChangeContentIdentity(){
        confirm(true);var first=scope.index(module,true);confirm(true);var second=scope.index(module,true);
        assertNotEquals(first.fingerprint(),second.fingerprint());assertNotEquals(first.chunks().get(0).id(),second.chunks().get(0).id());
        module.getFiles().get(0).setId(91L);assertEquals(second,scope.index(module,true));
    }
    @Test void insufficientProviderAssessmentCannotMarkOutlineAsInstruction(){
        confirm(true);
        when(chat.prompt().system(startsWith("Evaluate only")).user(anyString()).call().content()).thenReturn("{\"relevant\":true,\"sufficient\":false}");
        assertEquals("INSTRUCTIONAL_SOURCES_REQUIRED",assertThrows(ResponseStatusException.class,()->generation.generateRevision(module)).getReason());
        assertFalse(scope.read(module.getSourceScope()).instructionVerified());
    }
    @Test void instructionalGenerationSendsOnlySelectedTextAndRequiresExactSourceIds()throws Exception{
        confirm(true);String id=scope.index(module,true).chunks().get(0).id();
        when(chat.prompt().system(startsWith("Evaluate only")).user(anyString()).call().content()).thenReturn("{\"relevant\":true,\"sufficient\":true}");
        var response=json.writeValueAsString(Map.of("valid",true,"objectives","Learn","knowledge","Evidence-1","examples","Supported example","summary","Summary","sourceIds",List.of(id)));
        var spec=mock(ChatClient.ChatClientRequestSpec.class,RETURNS_DEEP_STUBS);
        when(chat.prompt().system(startsWith("Generate a lesson ONLY"))).thenReturn(spec);when(spec.user(anyString())).thenReturn(spec);when(spec.call().content()).thenReturn(response);
        generation.generateRevision(module);scope.requirePublishedReady(module);
        var input=org.mockito.ArgumentCaptor.forClass(String.class);verify(spec).user(input.capture());
        assertTrue(input.getValue().contains("Evidence-1"));assertFalse(input.getValue().contains("Evidence-2"));
        verify(spec,never()).user(any(java.util.function.Consumer.class));
        confirm(true);when(spec.call().content()).thenReturn(response);
        assertEquals(AiGenerationStatus.FAILED,generation.generateRevision(module).getAiGenerationStatus());
        assertFalse(scope.read(module.getSourceScope()).instructionVerified());
    }
    @Test void additiveMigrationPreservesLegacyRowsAndSnapshotsRoundTrip()throws Exception{
        confirm(false);String snapshot=module.getSourceScope();
        try(var connection=java.sql.DriverManager.getConnection("jdbc:h2:mem:scope_migration;MODE=MySQL");var sql=connection.createStatement()){
            sql.execute("CREATE TABLE ai_learning_modules(id BIGINT PRIMARY KEY, lesson_text LONGTEXT)");
            sql.execute("CREATE TABLE mentor_messages(id BIGINT PRIMARY KEY, message LONGTEXT)");
            sql.execute("INSERT INTO mentor_messages VALUES(1,'Legacy conversation')");
            sql.execute("INSERT INTO ai_learning_modules VALUES(1,'Legacy published content')");
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.FileSystemResource("database/V20261118_01__lesson_source_scope.sql"));
            try(var rows=sql.executeQuery("SELECT message,source_selection_id FROM mentor_messages")){assertTrue(rows.next());assertEquals("Legacy conversation",rows.getString(1));assertNull(rows.getString(2));}
            try(var rows=sql.executeQuery("SELECT lesson_text,source_scope FROM ai_learning_modules")){assertTrue(rows.next());assertEquals("Legacy published content",rows.getString(1));assertNull(rows.getString(2));}
            try(var insert=connection.prepareStatement("INSERT INTO ai_learning_modules VALUES(2,'Draft',?)")){insert.setString(1,snapshot);insert.executeUpdate();}
            try(var rows=sql.executeQuery("SELECT source_scope FROM ai_learning_modules WHERE id=2")){assertTrue(rows.next());assertEquals(scope.read(snapshot),scope.read(rows.getString(1)));}
        }
    }
}
