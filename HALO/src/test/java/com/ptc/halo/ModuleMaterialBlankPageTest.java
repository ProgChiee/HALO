package com.ptc.halo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptc.halo.component.ModuleMaterialIndex;
import com.ptc.halo.entity.*;
import com.ptc.halo.service.LessonStoragePaths;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ModuleMaterialBlankPageTest {
    @TempDir Path directory;
    AiLearningFileEntity pdf(long id, AiLearningModuleEntity module, String... pages) throws Exception {
        Path path=directory.resolve(id+".pdf");
        try(var document=new PDDocument()) {
            for(String text:pages) {
                var page=new PDPage();document.addPage(page);
                if(!text.isEmpty()) try(var content=new PDPageContentStream(document,page)) {
                    content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
                    content.newLineAtOffset(40,700);content.showText(text);content.endText();
                }
            }
            document.save(path.toFile());
        }
        var file=mock(AiLearningFileEntity.class);
        when(file.getId()).thenReturn(id);when(file.getModule()).thenReturn(module);
        when(file.getOriginalFileName()).thenReturn(id+".pdf");when(file.getFilePath()).thenReturn(path.toString());
        when(file.getFileType()).thenReturn("application/pdf");return file;
    }
    AiLearningModuleEntity module(){var m=mock(AiLearningModuleEntity.class);when(m.getId()).thenReturn(41L);return m;}
    ChatClient.ChatClientRequestSpec transcription(ChatClient chat) {
        var spec=mock(ChatClient.ChatClientRequestSpec.class,RETURNS_DEEP_STUBS);
        when(chat.prompt().system(anyString())).thenReturn(spec);
        when(spec.user(any(java.util.function.Consumer.class))).thenReturn(spec);return spec;
    }
    ModuleMaterialIndex index(ChatClient chat){return new ModuleMaterialIndex(chat,new ObjectMapper(),new LessonStoragePaths(directory.toString()));}

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"   "})
    void blankPagesDoNotDiscardReadablePagesOrShiftCitations(String response) throws Exception {
        var m=module();var chat=mock(ChatClient.class,RETURNS_DEEP_STUBS);var spec=transcription(chat);
        when(spec.call().content()).thenReturn(response);
        var file=pdf(29,m,"Hospitality guest service.","","Food safety and hygiene.","");when(m.getFiles()).thenReturn(List.of(file));
        var index=index(chat);var result=index.getOrBuild(m);
        assertEquals(List.of(1,3),result.chunks().stream().map(ModuleMaterialIndex.Chunk::page).toList());
        assertTrue(result.chunks().stream().allMatch(c->c.fileId().equals(29L)&&c.source().equals("29.pdf")));
        assertEquals("Hospitality guest service.",result.chunks().get(0).text());
        assertEquals("Food safety and hygiene.",result.chunks().get(1).text());
        clearInvocations(chat);
        assertEquals(result,index.getOrBuild(m));verifyNoInteractions(chat);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"   "})
    void entirelyBlankFileStillFailsEvenAlongsideReadableFile(String response) throws Exception {
        var m=module();var chat=mock(ChatClient.class,RETURNS_DEEP_STUBS);var spec=transcription(chat);
        when(spec.call().content()).thenReturn(response);
        var blank=pdf(30,m,"","");when(m.getFiles()).thenReturn(List.of(blank));
        assertEquals("MODULE_MATERIAL_UNREADABLE",assertThrows(ResponseStatusException.class,()->index(chat).getOrBuild(m)).getReason());
        var readable=pdf(29,m,"Food safety.");when(m.getFiles()).thenReturn(List.of(readable,blank));
        assertEquals("MODULE_MATERIAL_UNREADABLE",assertThrows(ResponseStatusException.class,()->index(chat).getOrBuild(m)).getReason());
        assertFalse(Files.exists(directory.resolve(".module-index/41.json")));
    }
    @Test void successfulTranscriptionRetainsItsOriginalPageNumber() throws Exception {
        var m=module();var chat=mock(ChatClient.class,RETURNS_DEEP_STUBS);var spec=transcription(chat);
        when(spec.call().content()).thenReturn("Kitchen hygiene.");
        var file=pdf(29,m,"Guest service.","");when(m.getFiles()).thenReturn(List.of(file));
        var result=index(chat).getOrBuild(m);
        assertEquals(2,result.chunks().get(1).page());assertEquals("Kitchen hygiene.",result.chunks().get(1).text());
    }
    @Test void providerExceptionsAreNotSilentlyTreatedAsBlankPages() throws Exception {
        var m=module();var chat=mock(ChatClient.class,RETURNS_DEEP_STUBS);var spec=transcription(chat);
        when(spec.call().content()).thenThrow(new IllegalStateException("provider failed"));
        var file=pdf(29,m,"Guest service.","");when(m.getFiles()).thenReturn(List.of(file));
        assertEquals("MODULE_INDEX_UNAVAILABLE",assertThrows(ResponseStatusException.class,()->index(chat).getOrBuild(m)).getReason());
        assertFalse(Files.exists(directory.resolve(".module-index/41.json")));
    }

    @Test void revisionBuildCannotReplaceLiveCache() throws Exception {
        var live=module();var draft=module();var chat=mock(ChatClient.class,RETURNS_DEEP_STUBS);
        var originalFile=pdf(1,live,"Original hospitality lesson.");var revisionFile=pdf(2,draft,"Revised hospitality lesson.");
        when(live.getFiles()).thenReturn(List.of(originalFile));
        when(draft.getFiles()).thenReturn(List.of(revisionFile));
        var index=index(chat);var original=index.getOrBuild(live);
        var cache=directory.resolve(".module-index/41.json");var bytes=Files.readAllBytes(cache);
        assertEquals(2L,index.buildRevision(draft).chunks().get(0).fileId());assertArrayEquals(bytes,Files.readAllBytes(cache));
        assertEquals(original,index.getOrBuild(live));index.invalidate(41L);assertFalse(Files.exists(cache));
        assertEquals(2L,index.getOrBuild(draft).chunks().get(0).fileId());
    }
}
