package com.ptc.halo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

/** One bounded child heap at a time. Deadlines terminate the parser, not just the HTTP wait. */
@Component
public class ObtlExtractionProcess {
    public enum Reason { WORKER_START, WORKER_TIMEOUT, WORKER_NONZERO_EXIT, WORKER_INPUT, WORKER_OUTPUT_LIMIT,
        WORKER_OUTPUT_READ, EMPTY_PARSER_OUTPUT, PARSER_JSON_INVALID, PARSER_SCHEMA_INVALID }
    public static final class Failure extends Exception {
        private final Reason reason; private final Integer exitCode;
        Failure(Reason reason,Integer exitCode){super(reason.name());this.reason=reason;this.exitCode=exitCode;}
        public Reason reason(){return reason;}
        public Integer exitCode(){return exitCode;}
    }
    protected Process start(ProcessBuilder builder)throws java.io.IOException{return builder.start();}
    private static final Semaphore SLOT=new Semaphore(1);
    public ObtlPdfText.Extracted extract(byte[] bytes) throws Exception {
        if(bytes.length>3*1024*1024)throw new IllegalArgumentException("SOURCE_SIZE_INVALID");
        if(!SLOT.tryAcquire())throw new IllegalStateException("SOURCE_EXTRACTION_BUSY");
        Process child=null;
        ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"obtl-output");t.setDaemon(true);return t;});
        try {
            String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
            List<String> command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-Xmx96m","-XX:MaxMetaspaceSize=64m","-XX:+UseSerialGC","-XX:ActiveProcessorCount=1"));
            if(cp.endsWith(".jar")&&!cp.contains(java.io.File.pathSeparator)) {
                command.add("-Dloader.main="+ObtlWorker.class.getName());
                command.addAll(List.of("-cp",cp,"org.springframework.boot.loader.launch.PropertiesLauncher"));
            } else command.addAll(List.of("-cp",cp,ObtlWorker.class.getName()));
            var builder=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
            for(String key:List.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS"))builder.environment().remove(key);
            try { child=start(builder); } catch(java.io.IOException|SecurityException e){throw new Failure(Reason.WORKER_START,null);}
            final Process process=child;
            Future<byte[]> output=io.submit(()->process.getInputStream().readNBytes(8*1024*1024+1));
            // Input is small and the child reads it before parsing; bound this write as well.
            CompletableFuture<Void> input=CompletableFuture.runAsync(()->{
                try(var stream=process.getOutputStream()){stream.write(bytes);}
                catch(java.io.IOException e){throw new CompletionException(e);}
            });
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
            try { input.get(5,TimeUnit.SECONDS); }
            catch(ExecutionException e){
                if(child.waitFor(1,TimeUnit.SECONDS)&&child.exitValue()!=0)throw new Failure(Reason.WORKER_NONZERO_EXIT,child.exitValue());
                throw new Failure(Reason.WORKER_INPUT,null);
            }
            byte[] result=output.get(Math.max(1,end-System.nanoTime()),TimeUnit.NANOSECONDS);
            if(result.length>8*1024*1024)throw new Failure(Reason.WORKER_OUTPUT_LIMIT,null);
            if(!child.waitFor(Math.max(1,end-System.nanoTime()),TimeUnit.NANOSECONDS))throw new Failure(Reason.WORKER_TIMEOUT,null);
            if(child.exitValue()!=0)throw new Failure(Reason.WORKER_NONZERO_EXIT,child.exitValue());
            if(result.length==0)throw new Failure(Reason.EMPTY_PARSER_OUTPUT,0);
            ObtlPdfText.Extracted parsed;
            try {
                parsed=new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(result,ObtlPdfText.Extracted.class);
            }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new Failure(Reason.PARSER_JSON_INVALID,0);}
                if(parsed==null||parsed.checksum()==null||parsed.detection()==null||parsed.detection().pages()==null
                    ||parsed.detection().pages().isEmpty()||parsed.detection().pages().stream().anyMatch(p->p==null||p.text()==null))
                    throw new Failure(Reason.PARSER_SCHEMA_INVALID,0);
                return parsed;
        } catch(TimeoutException e){throw new Failure(Reason.WORKER_TIMEOUT,null);
        } catch(ExecutionException e){throw new Failure(Reason.WORKER_OUTPUT_READ,child!=null&&!child.isAlive()?child.exitValue():null);
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw e;
        } finally {
            try { if(child!=null){child.destroyForcibly();child.waitFor(5,TimeUnit.SECONDS);} }
            finally { io.shutdownNow();SLOT.release(); }
        }
    }
}
