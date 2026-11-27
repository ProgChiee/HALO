package com.ptc.halo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name="halo.subject-cover.provider", havingValue="supabase")
public class SupabaseSubjectCoverStorage extends SubjectCoverStorage {
    static final String BUCKET = "halo-subject-covers";
    private final URI base;
    private final String secret;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public SupabaseSubjectCoverStorage(@Value("${SUPABASE_URL}") String url,
            @Value("${SUPABASE_SECRET_KEY}") String secret, ObjectMapper json) {
        super(System.getProperty("java.io.tmpdir"));
        this.base=URI.create(url);
        boolean loopback="http".equals(base.getScheme()) && "127.0.0.1".equals(base.getHost());
        if ((!loopback && !"https".equals(base.getScheme())) || base.getHost()==null || base.getUserInfo()!=null
                || base.getQuery()!=null || base.getFragment()!=null || !java.util.Set.of("","/").contains(base.getPath())
                || secret.isBlank()) throw new IllegalArgumentException("Invalid private subject cover configuration");
        this.secret=secret;
        this.json=json;
    }

    private HttpRequest.Builder request(String path) {
        var builder=HttpRequest.newBuilder(base.resolve("/storage/v1/"+path)).timeout(Duration.ofSeconds(15)).header("apikey",secret);
        if (!secret.startsWith("sb_secret_")) builder.header("Authorization","Bearer "+secret);
        return builder;
    }

    private byte[] send(HttpRequest request, int limit, boolean allowMissing) throws IOException {
        try {
            var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(var stream=response.body()) {
                int status=response.statusCode();
                if (status<200 || status>=300) {
                    if(allowMissing && status==404) return null;
                    if(allowMissing && status==400) {
                        JsonNode error=json.readTree(stream.readNBytes(4096));
                        if(error!=null && ("NoSuchKey".equals(error.path("code").asText()) ||
                                "404".equals(error.path("statusCode").asText()) && "Object not found".equalsIgnoreCase(error.path("message").asText()))) return null;
                    }
                    throw new IOException("Private subject cover storage HTTP "+status);
                }
                byte[] bytes=stream.readNBytes(limit+1);
                if(bytes.length>limit) throw new IOException("Oversized subject cover storage response");
                return bytes;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Subject cover storage interrupted");
        }
    }

    private String key(long id) throws IOException {
        if(id<=0) throw new IllegalArgumentException("Invalid subject ID");
        JsonNode bucket=json.readTree(send(request("bucket/"+BUCKET).GET().build(),16384,false));
        if(bucket==null || !bucket.path("public").isBoolean() || bucket.get("public").booleanValue()
                || !BUCKET.equals(bucket.path("id").asText())) throw new IOException("Private subject cover bucket required");
        return BUCKET+"/"+id;
    }

    @Override public byte[] read(long id) throws IOException {
        byte[] bytes=send(request("object/authenticated/"+key(id)+"?v="+UUID.randomUUID()).GET().build(),12*1024*1024,true);
        if(bytes!=null && bytes.length==0) throw new IOException("Empty subject cover");
        return bytes;
    }

    @Override public void install(long id, Path staged) throws IOException {
        byte[] data=Files.readAllBytes(staged);
        send(request("object/"+key(id)).header("x-upsert","true")
                .header("Content-Type",data[0]==(byte)0xff ? "image/jpeg" : "image/png")
                .POST(HttpRequest.BodyPublishers.ofByteArray(data)).build(),16384,false);
    }

    @Override public void remove(long id) throws IOException {
        String path=key(id);
        send(request("object/"+path).DELETE().build(),16384,true);
    }
}
