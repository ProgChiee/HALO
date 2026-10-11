package com.ptc.halo.service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** URL parsing only; never fetch a client-supplied address. */
public final class YoutubeVideoUrl {
 private YoutubeVideoUrl() {}
 public static String normalize(String value) {
  if(value==null || value.isBlank())return null;
  try {
   String text=value.strip();
   if(text.length()>255 || text.chars().anyMatch(c -> Character.isWhitespace(c) || c==92))throw new IllegalArgumentException();
   URI url=new URI(text);String host=url.getHost();
   if(!("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme())) || host==null || url.getRawUserInfo()!=null || url.getPort()!=-1)throw new IllegalArgumentException();
   host=host.toLowerCase(java.util.Locale.ROOT);String path=url.getRawPath(),id=null;
   if(host.equals("youtu.be")){if(path.matches("/[A-Za-z0-9_-]{11}"))id=path.substring(1);}
   else if(java.util.Set.of("youtube.com","www.youtube.com","m.youtube.com").contains(host)) {
    if(path.matches("/shorts/[A-Za-z0-9_-]{11}"))id=path.substring(8);
    else if(path.equals("/watch") && url.getRawQuery()!=null) {
     for(String part:url.getRawQuery().split("&")) {String[] pair=part.split("=",2);
      if(URLDecoder.decode(pair[0],StandardCharsets.UTF_8).equals("v")){if(id!=null || pair.length!=2)throw new IllegalArgumentException();id=URLDecoder.decode(pair[1],StandardCharsets.UTF_8);}
     }
    }
   }
   if(id==null || !id.matches("[A-Za-z0-9_-]{11}"))throw new IllegalArgumentException();
   return "https://www.youtube.com/watch?v="+id;
  } catch(Exception ignored){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_YOUTUBE_URL");}
 }
}
