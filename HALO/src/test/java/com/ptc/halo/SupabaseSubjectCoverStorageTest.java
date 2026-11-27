package com.ptc.halo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptc.halo.service.SupabaseSubjectCoverStorage;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SupabaseSubjectCoverStorageTest {
    @TempDir Path directory;

    @Test void privateStoreSurvivesClientReplacementAndRejectsFailedWrites() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var object=new AtomicReference<byte[]>();
        var fail=new AtomicBoolean();
        var isPublic=new AtomicBoolean();
        server.createContext("/storage/v1/", exchange -> {
            var path=exchange.getRequestURI().getPath();
            byte[] response={};
            int status=200;
            if (!"sb_secret_test".equals(exchange.getRequestHeaders().getFirst("apikey"))) status=401;
            else if (path.endsWith("/bucket/halo-subject-covers"))
                response=("{\"id\":\"halo-subject-covers\",\"public\":"+isPublic.get()+"}").getBytes();
            else if (!path.endsWith("/halo-subject-covers/42")) status=404;
            else switch(exchange.getRequestMethod()) {
                case "POST" -> { if(fail.get()) status=503; else object.set(exchange.getRequestBody().readAllBytes()); }
                case "DELETE" -> object.set(null);
                case "GET" -> {if(object.get()==null) status=404; else response=object.get();}
                default -> status=405;
            }
            exchange.sendResponseHeaders(status,response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            String url="http://127.0.0.1:"+server.getAddress().getPort();
            var store=new SupabaseSubjectCoverStorage(url,"sb_secret_test",new ObjectMapper());
            var original=directory.resolve("cover");
            byte[] png={(byte)137,80,78,71};
            Files.write(original,png);
            store.install(42,original);
            store=new SupabaseSubjectCoverStorage(url,"sb_secret_test",new ObjectMapper());
            assertArrayEquals(png,store.read(42));
            final var restarted=store;
            Files.write(original,new byte[]{(byte)255,(byte)216});
            fail.set(true);
            assertThrows(IOException.class,()->restarted.install(42,original));
            assertArrayEquals(png,store.read(42));
            fail.set(false);
            store.install(42,original);
            assertArrayEquals(Files.readAllBytes(original),store.read(42));
            isPublic.set(true);
            assertThrows(IOException.class,()->restarted.remove(42));
            isPublic.set(false);
            store.remove(42);
            assertNull(store.read(42));
        } finally {server.stop(0);}
    }
}
