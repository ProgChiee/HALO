package com.ptc.halo.service;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Private, normalized raster files. No client filename or URL is used as a path. */
@Service
public class SubjectCoverStorage {
    public static final int MAX_BYTES=3*1024*1024;
    private final Path root;
    public SubjectCoverStorage(@Value("${halo.subject-cover-directory:uploads/subject-covers}") String directory) {
        root=Path.of(directory).toAbsolutePath().normalize();
    }
    private Path path(long id) {
        if(id<=0)throw invalid();
        return root.resolve(id+".png");
    }
    public byte[] read(long id) throws IOException {
        Path file=path(id);
        return Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)?Files.readAllBytes(file):null;
    }
    public Path prepare(MultipartFile upload) throws IOException {
        if(upload==null || upload.isEmpty() || upload.getSize()>MAX_BYTES)throw invalid();
        byte[] bytes;
        try(var input=upload.getInputStream()){bytes=input.readNBytes(MAX_BYTES+1);}
        if(bytes.length>MAX_BYTES)throw invalid();
        BufferedImage image;
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw invalid();
            var reader=readers.next();
            try {
                var format=reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if(!java.util.Set.of("png","jpeg","jpg","webp").contains(format))throw invalid();
                reader.setInput(input);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1 || height<1 || width>8192 || height>8192 || (long)width*height>16_000_000)throw invalid();
                image=reader.read(0);
                if(image==null)throw invalid();
            } finally {reader.dispose();}
        } catch(IOException|IllegalArgumentException malformed){throw invalid();}
        double scale=Math.min(1,1600.0/Math.max(image.getWidth(),image.getHeight()));
        var clean=new BufferedImage(Math.max(1,(int)(image.getWidth()*scale)),Math.max(1,(int)(image.getHeight()*scale)),BufferedImage.TYPE_INT_ARGB);
        var graphics=clean.createGraphics();
        try{graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);graphics.drawImage(image,0,0,clean.getWidth(),clean.getHeight(),null);}finally{graphics.dispose();}
        Files.createDirectories(root);
        Path temp=Files.createTempFile(root,".cover-",".tmp");
        try{if(!ImageIO.write(clean,"png",temp.toFile()))throw invalid();return temp;}
        catch(Exception failure){Files.deleteIfExists(temp);throw failure;}
    }
    public void install(long id,Path staged) throws IOException {
        Files.move(staged,path(id),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    public void remove(long id) throws IOException {Files.deleteIfExists(path(id));}
    public void discard(Path staged) {
        try{Files.deleteIfExists(staged);}catch(IOException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("subject_cover_temporary_cleanup_failed");}
    }
    public record Deleted(long subjectId) {}
    @TransactionalEventListener
    public void deleted(Deleted event) {
        try{remove(event.subjectId());}catch(IOException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("deleted_subject_cover_cleanup_failed subjectId={}",event.subjectId());}
    }
    private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_SUBJECT_COVER: Choose a valid JPEG, PNG or WebP up to 3 MB and 16 megapixels.");}
}
