package com.ptc.halo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** No public/signed URLs. The server credential never leaves this client. */
@Component @Primary
@ConditionalOnProperty(name="halo.lesson-storage.provider", havingValue="supabase")
public class SupabaseLessonMaterialStorage implements LessonMaterialStorage {
    public static final String BUCKET = "halo-learning-materials";
    private final URI base;
    private final String secret;
    private final LessonObjectKeys keys;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json = new ObjectMapper();

    public SupabaseLessonMaterialStorage(@Value("${SUPABASE_URL:}") String url,
            @Value("${SUPABASE_SECRET_KEY:}") String secret,
            @Value("${halo.lesson-storage.legacy-roots:}") String roots) {
        base = URI.create(url.replaceAll("/+$", ""));
        boolean local = "http".equals(base.getScheme()) && java.util.Set.of("localhost","127.0.0.1","[::1]").contains(base.getHost());
        if ((!"https".equals(base.getScheme()) && !local) || base.getHost()==null || base.getUserInfo()!=null
                || base.getQuery()!=null || base.getFragment()!=null || !base.getPath().isEmpty()
                || secret==null || secret.isBlank()) throw new IllegalArgumentException("Invalid private storage configuration");
        this.secret=secret; keys=new LessonObjectKeys(roots);
    }
    @Override public String key(String reference) { return keys.key(reference); }
    private String encoded(String reference) {
        return java.util.Arrays.stream(key(reference).split("/")).map(s -> URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+","%20")).collect(java.util.stream.Collectors.joining("/"));
    }
    private HttpRequest.Builder request(String path) {
        var builder=HttpRequest.newBuilder(base.resolve("/storage/v1/"+path)).timeout(Duration.ofSeconds(30)).header("apikey",secret);
        // Legacy service-role JWTs remain supported. Opaque sb_secret keys belong only in apikey.
        if (!secret.startsWith("sb_secret_")) builder.header("Authorization","Bearer "+secret);
        return builder;
    }
    private byte[] send(HttpRequest request, int limit) throws IOException {
        try {
            var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(var input=response.body()) {
                if(response.statusCode()==404) throw new FileNotFoundException("Material unavailable");
                if(response.statusCode()<200 || response.statusCode()>299) throw new IOException("Private storage request failed ("+response.statusCode()+")");
                byte[] bytes=input.readNBytes(limit+1);
                if(bytes.length>limit) throw new IOException("Private storage response too large");
                return bytes;
            }
        } catch(InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException("Private storage request interrupted"); }
    }
    private void requirePrivate() throws IOException {
        var bucket=json.readTree(send(request("bucket/"+BUCKET).GET().build(),64*1024));
        if(!bucket.has("public") || !bucket.get("public").isBoolean() || bucket.get("public").booleanValue()) throw new IOException("Learning materials bucket must be private");
    }
    @Override public byte[] read(String reference) throws IOException {
        String path=encoded(reference); requirePrivate();
        byte[] bytes=send(request("object/authenticated/"+BUCKET+"/"+path).GET().build(),MAX_BYTES);
        if(bytes.length==0) throw new IOException("Empty material");
        return bytes;
    }
    @Override public void writeNew(String key,byte[] bytes,String mediaType) throws IOException {
        String path=encoded(key); requirePrivate();
        send(request("object/"+BUCKET+"/"+path).header("Content-Type",mediaType).header("x-upsert","false")
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),64*1024);
    }
    @Override public void delete(String reference) throws IOException {
        String key=key(reference); requirePrivate();
        send(request("object/"+BUCKET).header("Content-Type","application/json")
            .method("DELETE",HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(java.util.Map.of("prefixes",java.util.List.of(key))))).build(),64*1024);
    }
}
