package com.ptc.halo.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** All persisted lesson paths are resolved against one absolute, configured root. */
@Component
public class LessonStoragePaths implements LessonMaterialStorage {
    private final Path root;

    public LessonStoragePaths(@Value("${halo.lesson-storage.root}") String configuredRoot) {
        Path configured = Path.of(configuredRoot);
        if (!configured.isAbsolute()) throw new IllegalArgumentException("Lesson storage root must be absolute");
        root = configured.normalize();
    }

    public Path resolve(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) throw invalid();
        String portable = storedPath.replace('\\', '/');
        // Compatibility for the historical relative DB representation; never consult cwd.
        if (portable.startsWith("uploads/lessons/")) portable = portable.substring("uploads/lessons/".length());
        Path record = Path.of(portable);
        Path resolved = (record.isAbsolute() ? record : root.resolve(record)).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) throw invalid();
        // Reject symlink/junction escapes as well as lexical traversal, including new files.
        try {
            Path existingRoot = root;
            while (!Files.exists(existingRoot, LinkOption.NOFOLLOW_LINKS)) existingRoot = existingRoot.getParent();
            Path realRoot = existingRoot.toRealPath().resolve(existingRoot.relativize(root)).normalize();
            Path existing = resolved;
            while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
            Path real = existing.toRealPath().resolve(existing.relativize(resolved)).normalize();
            if (!real.startsWith(realRoot)) throw invalid();
        } catch (IOException error) { throw new IllegalArgumentException("Lesson storage path cannot be resolved", error); }
        return resolved;
    }

    public String storedPath(Path path) {
        return root.relativize(resolve(path.toString())).toString().replace('\\', '/');
    }

    @Override public String key(String reference) { return storedPath(resolve(reference)); }
    @Override public byte[] read(String reference) throws IOException {
        Path path = resolve(reference);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) throw new java.io.FileNotFoundException("Material unavailable");
        try (var stream = Files.newInputStream(path)) {
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("Invalid material size");
            return bytes;
        }
    }
    @Override public void writeNew(String key, byte[] bytes, String mediaType) throws IOException {
        Path path = resolve(key);
        Files.createDirectories(path.getParent());
        Files.createFile(path);
        try (var stream = Files.newOutputStream(path)) { stream.write(bytes); }
        catch (IOException | RuntimeException failure) { Files.deleteIfExists(path); throw failure; }
    }
    @Override public void delete(String reference) throws IOException { Files.deleteIfExists(resolve(reference)); }

    private IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid lesson storage path"); }
}
