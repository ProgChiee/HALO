package com.ptc.halo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.file.*;
import java.io.*;
import javax.imageio.ImageIO;

/** Private raster storage; IDs come exclusively from the authenticated/validated target. */
@Service
public class ProfilePhotoService {
    private final Path root;
    public ProfilePhotoService(@Value("${halo.profile-photo-directory:uploads/student-photos}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }
    public byte[] read(long userId) throws IOException {
        Path file = root.resolve(userId + ".png");
        return Files.exists(file) ? Files.readAllBytes(file) : null;
    }
    public void save(long userId, MultipartFile upload) throws IOException {
        if (upload == null || upload.isEmpty() || upload.getSize() > 2 * 1024 * 1024) throw invalid();
        byte[] bytes;
        try (var input = upload.getInputStream()) { bytes = input.readNBytes(2 * 1024 * 1024 + 1); }
        if (bytes.length > 2 * 1024 * 1024) throw invalid();
        byte[] clean;
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) throw invalid();
                reader.setInput(input);
                if (reader.getWidth(0) > 1024 || reader.getHeight(0) > 1024) throw invalid();
                var image = reader.read(0);
                if (image == null) throw invalid();
                var output = new ByteArrayOutputStream();
                ImageIO.write(image, "png", output);
                clean = output.toByteArray();
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException error) { throw invalid(); }
        Files.createDirectories(root);
        Path temp = Files.createTempFile(root, ".photo-", ".tmp");
        try {
            Files.write(temp, clean);
            Files.move(temp, root.resolve(userId + ".png"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    private ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PROFILE_PHOTO"); }
}
