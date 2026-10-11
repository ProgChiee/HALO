package com.ptc.halo;
import com.ptc.halo.service.YoutubeVideoUrl;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class YoutubeVideoUrlTest {
 @Test void acceptsCommonFormatsAndNormalizes(){for(String url:java.util.List.of("https://www.youtube.com/watch?v=abcdefghijk&list=ignored","https://youtu.be/abcdefghijk?t=2","https://www.youtube.com/shorts/abcdefghijk"))assertEquals("https://www.youtube.com/watch?v=abcdefghijk",YoutubeVideoUrl.normalize(url));assertNull(YoutubeVideoUrl.normalize(" "));}
 @Test void rejectsUnsafeAndMalformed(){for(String url:java.util.List.of("bad","https://evil.example/watch?v=abcdefghijk","https://youtube.com.evil.example/watch?v=abcdefghijk","https://youtube.com/watch?v=short","https://youtube.com/watch","javascript:alert(1)","https://user@youtube.com/watch?v=abcdefghijk","https://youtu.be/abcdefghijk/extra","https://youtube.com/watch?v=abcdefghijk&v=otherother1")){var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->YoutubeVideoUrl.normalize(url));assertEquals(400,error.getStatusCode().value());assertEquals("INVALID_YOUTUBE_URL",error.getReason());}}
}
