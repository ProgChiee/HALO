package com.ptc.halo.service;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ObtlExtractionDiagnosticsTest {
    Process child(String output,int exit,boolean finished)throws Exception {
        var p=mock(Process.class);
        when(p.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(p.getOutputStream()).thenReturn(OutputStream.nullOutputStream());
        when(p.waitFor(anyLong(),any(TimeUnit.class))).thenReturn(finished);
        when(p.exitValue()).thenReturn(exit);
        when(p.destroyForcibly()).thenReturn(p);
        return p;
    }
    ObtlExtractionProcess runner(Process p){return new ObtlExtractionProcess(){
        @Override protected Process start(ProcessBuilder builder){
            assertEquals(ProcessBuilder.Redirect.DISCARD,builder.redirectError());
            assertFalse(builder.environment().containsKey("JAVA_TOOL_OPTIONS"));
            return p;
        }
    };}
    @Test void startupDoesNotRetainSensitiveExceptionDetails(){
        var worker=new ObtlExtractionProcess(){@Override protected Process start(ProcessBuilder b)throws IOException{throw new IOException("SECRET_PRIVATE_PATH");}};
        var failure=assertThrows(ObtlExtractionProcess.Failure.class,()->worker.extract(new byte[]{1}));
        assertEquals(ObtlExtractionProcess.Reason.WORKER_START,failure.reason());
        assertNull(failure.getCause());assertFalse(failure.toString().contains("SECRET"));
    }
    @Test void nonzeroExitIsDistinctAndProcessIsTerminated()throws Exception{
        var p=child("",137,true);
        var failure=assertThrows(ObtlExtractionProcess.Failure.class,()->runner(p).extract(new byte[]{1}));
        assertEquals(ObtlExtractionProcess.Reason.WORKER_NONZERO_EXIT,failure.reason());assertEquals(137,failure.exitCode());
        verify(p).destroyForcibly();
    }
    @Test void timeoutIsDistinctAndReleasesWorkerSlot()throws Exception{
        var p=child("",0,false);
        var failure=assertThrows(ObtlExtractionProcess.Failure.class,()->runner(p).extract(new byte[]{1}));
        assertEquals(ObtlExtractionProcess.Reason.WORKER_TIMEOUT,failure.reason());verify(p).destroyForcibly();
        assertEquals(ObtlExtractionProcess.Reason.PARSER_SCHEMA_INVALID,
            assertThrows(ObtlExtractionProcess.Failure.class,()->runner(child("{}",0,true)).extract(new byte[]{1})).reason());
    }
    @Test void malformedAndEmptyOutputsNeverExposeBodies()throws Exception{
        for(String output:new String[]{"","null","{}","SECRET_PDF_CONTENT","{\"checksum\":", "{} {}"}){
            var failure=assertThrows(ObtlExtractionProcess.Failure.class,()->runner(child(output,0,true)).extract(new byte[]{1}));
            assertEquals(output.isEmpty()?ObtlExtractionProcess.Reason.EMPTY_PARSER_OUTPUT:
                output.equals("null")||output.equals("{}")?ObtlExtractionProcess.Reason.PARSER_SCHEMA_INVALID:
                ObtlExtractionProcess.Reason.PARSER_JSON_INVALID,failure.reason());
            assertEquals(0,failure.exitCode());assertNull(failure.getCause());
        }
    }
    @Test void pipeReadFailureIsNotMisreportedAsJsonFailure()throws Exception{
        var p=child("",137,true);
        when(p.getInputStream()).thenReturn(new InputStream(){@Override public int read()throws IOException{throw new IOException("SECRET_PIPE_DETAILS");}});
        var failure=assertThrows(ObtlExtractionProcess.Failure.class,()->runner(p).extract(new byte[]{1}));
        assertEquals(ObtlExtractionProcess.Reason.WORKER_OUTPUT_READ,failure.reason());assertEquals(137,failure.exitCode());assertNull(failure.getCause());
    }
    @Test void publicFailureAndStructuredLogsNeverIncludePathsOrNestedMessages()throws Exception{
        var storage=mock(LessonMaterialStorage.class);var worker=mock(ObtlExtractionProcess.class);
        var scope=new LessonSourceScope(new com.fasterxml.jackson.databind.ObjectMapper(),storage,worker);
        var module=new com.ptc.halo.entity.AiLearningModuleEntity();var file=new com.ptc.halo.entity.AiLearningFileEntity();
        file.setFilePath("SECRET_KEY");file.setOriginalFileName("SECRET_NAME");file.setFileType("application/pdf");module.addFile(file);
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(LessonSourceScope.class);
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();appender.start();logger.addAppender(appender);
        try {
            when(storage.read(anyString())).thenThrow(new IOException("SECRET",new RuntimeException("SECRET_TOKEN")));
            var e=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->scope.extract(module));
            assertEquals(422,e.getStatusCode().value());assertEquals("SOURCE_EXTRACTION_FAILED",e.getReason());
            var event=appender.list.get(0);assertTrue(event.getFormattedMessage().contains("reason=STORAGE_READ"));
            assertFalse(event.getFormattedMessage().contains("SECRET"));assertNull(event.getThrowableProxy());
            for(int status:new int[]{401,403,500}){
                doThrow(new SupabaseLessonMaterialStorage.HttpFailure(status,"BUCKET_CHECK")).when(storage).read(anyString());
                assertThrows(org.springframework.web.server.ResponseStatusException.class,()->scope.extract(module));
                var last=appender.list.get(appender.list.size()-1);
                assertTrue(last.getFormattedMessage().contains("httpStatus="+status));assertNull(last.getThrowableProxy());
            }
        }finally{logger.detachAppender(appender);appender.stop();}
    }
}
