package com.ptc.halo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

/** One bounded child heap at a time. Deadlines terminate the parser, not just the HTTP wait. */
@Component
public class ObtlExtractionProcess {
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
            child=builder.start(); final Process process=child;
            Future<byte[]> output=io.submit(()->process.getInputStream().readNBytes(8*1024*1024+1));
            // Input is small and the child reads it before parsing; bound this write as well.
            CompletableFuture<Void> input=CompletableFuture.runAsync(()->{
                try(var stream=process.getOutputStream()){stream.write(bytes);}
                catch(java.io.IOException e){throw new CompletionException(e);}
            });
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
            input.get(5,TimeUnit.SECONDS);
            byte[] result=output.get(Math.max(1,end-System.nanoTime()),TimeUnit.NANOSECONDS);
            if(result.length>8*1024*1024||!child.waitFor(Math.max(1,end-System.nanoTime()),TimeUnit.NANOSECONDS)||child.exitValue()!=0)
                throw new IllegalStateException("SOURCE_EXTRACTION_FAILED");
            return new ObjectMapper().readValue(result,ObtlPdfText.Extracted.class);
        } finally {
            try { if(child!=null){child.destroyForcibly();child.waitFor(5,TimeUnit.SECONDS);} }
            finally { io.shutdownNow();SLOT.release(); }
        }
    }
}
