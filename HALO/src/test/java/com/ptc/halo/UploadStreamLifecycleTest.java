package com.ptc.halo;

import com.ptc.halo.entity.AiLearningModuleEntity;
import com.ptc.halo.service.FileUploadService;
import com.ptc.halo.service.LessonStoragePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadStreamLifecycleTest {
    @TempDir Path directory;

    record TrackedFile(MultipartFile file, AtomicInteger opens, AtomicInteger closes) {}

    TrackedFile tracked(byte[] bytes, boolean failReading) throws Exception {
        var file = mock(MultipartFile.class);
        var opens = new AtomicInteger();
        var closes = new AtomicInteger();
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) bytes.length);
        when(file.getOriginalFilename()).thenReturn("original.png");
        when(file.getInputStream()).thenAnswer(call -> {
            opens.incrementAndGet();
            return new FilterInputStream(new ByteArrayInputStream(bytes)) {
                @Override public int read(byte[] target, int offset, int length) throws IOException {
                    if (failReading) throw new IOException("Forced input read failure");
                    return super.read(target, offset, length);
                }
                @Override public void close() throws IOException {
                    closes.incrementAndGet();
                    super.close();
                }
            };
        });
        return new TrackedFile(file, opens, closes);
    }

    byte[] validImage() throws IOException {
        var bytes = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    String upload(FileUploadService service, TrackedFile file, boolean existingModule) throws IOException {
        return existingModule ? service.uploadFile(file.file(), new AiLearningModuleEntity()).getFilePath()
                : service.uploadFile(file.file());
    }

    void assertClosed(TrackedFile file) {
        assertEquals(1, file.opens().get());
        assertEquals(1, file.closes().get());
    }

    void begin() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    void end(int status) {
        try {
            for (var callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCompletion(status);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test void bothOverloadsCloseInputOnSuccessfulUpload() throws Exception {
        var service = new FileUploadService(new LessonStoragePaths(directory.toString()));
        for (boolean existing : new boolean[]{false, true}) {
            var file = tracked(validImage(), false);
            begin();
            try {
                var stored = upload(service, file, existing);
                assertClosed(file);
                assertArrayEquals(validImage(), Files.readAllBytes(directory.resolve(stored)));
            } finally { end(TransactionSynchronization.STATUS_COMMITTED); }
        }
    }

    @Test void bothOverloadsCloseInputOnReadAndValidationExceptions() throws Exception {
        var service = new FileUploadService(new LessonStoragePaths(directory.toString()));
        for (boolean existing : new boolean[]{false, true}) {
            for (boolean readFailure : new boolean[]{false, true}) {
                var file = tracked(readFailure ? validImage() : new byte[]{1, 2, 3}, readFailure);
                assertThrows(ResponseStatusException.class, () -> upload(service, file, existing));
                assertClosed(file);
            }
        }
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test void closedStreamsStayClosedAndFilesAreRemovedOnRollback() throws Exception {
        var service = new FileUploadService(new LessonStoragePaths(directory.toString()));
        for (boolean existing : new boolean[]{false, true}) {
            var file = tracked(validImage(), false);
            begin();
            try {
                upload(service, file, existing);
                assertClosed(file);
            } finally { end(TransactionSynchronization.STATUS_ROLLED_BACK); }
            assertClosed(file);
            try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
        }
    }

    @Test void inputIsClosedBeforeLaterStorageFailure() throws Exception {
        var paths = mock(LessonStoragePaths.class);
        when(paths.resolve(anyString())).thenReturn(directory.resolve("probe"));
        var service = new FileUploadService(paths);
        when(paths.resolve(anyString())).thenThrow(new IllegalArgumentException("Forced storage failure"));
        for (boolean existing : new boolean[]{false, true}) {
            var file = tracked(validImage(), false);
            assertThrows(IllegalArgumentException.class, () -> upload(service, file, existing));
            assertClosed(file);
        }
    }

    @Test void batchValidationClosesEarlierAndFailingInputStreams() throws Exception {
        var service = new FileUploadService(new LessonStoragePaths(directory.toString()));
        var valid = tracked(validImage(), false);
        var failing = tracked(validImage(), true);
        assertThrows(ResponseStatusException.class, () -> service.validateFiles(java.util.List.of(valid.file(), failing.file())));
        assertClosed(valid);
        assertClosed(failing);
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }
}
