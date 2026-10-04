package com.ptc.halo.service;

import com.ptc.halo.entity.AiLearningFileEntity;
import com.ptc.halo.entity.AiLearningModuleEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;

@Service
public class FileUploadService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileUploadService.class);
    private final LessonStoragePaths storage;

    public FileUploadService(LessonStoragePaths storage) throws IOException {
        this.storage = storage;
        Files.createDirectories(storage.resolve(".storage-probe").getParent());
    }

    public static final int MAX_FILES = 10;
    public static final int MAX_BYTES = 3 * 1024 * 1024;
    public record ValidatedUpload(String originalName, String mediaType, byte[] bytes) {}

    public void checkFileCount(int existing, int incoming) {
        if (existing + incoming > MAX_FILES) throw problem(409, "MODULE_FILE_LIMIT_EXCEEDED");
    }

    public java.util.List<ValidatedUpload> validateFiles(java.util.List<MultipartFile> files) {
        if (files == null) return java.util.List.of();
        checkFileCount(0, files.size());
        return files.stream().map(this::validate).toList();
    }

    public ValidatedUpload validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw problem(400, "UPLOAD_FILE_UNREADABLE");
        if (file.getSize() > MAX_BYTES) throw problem(413, "UPLOAD_FILE_TOO_LARGE");
        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())
            throw problem(400, "UPLOAD_FILENAME_REQUIRED");
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw problem(413, "UPLOAD_FILE_TOO_LARGE");
            if (bytes.length == 0) throw problem(400, "UPLOAD_FILE_UNREADABLE");
            String type;
            if (startsWith(bytes, new byte[]{37,80,68,70,45})) {
                type = "application/pdf";
                try (var pdf = org.apache.pdfbox.Loader.loadPDF(bytes)) {
                    if (pdf.isEncrypted() || pdf.getNumberOfPages() == 0) throw problem(400, "UPLOAD_FILE_UNREADABLE");
                    // Parse page streams as well as the document directory. Textless scans remain supported.
                    for (var page : pdf.getPages()) {
                        try (var content = page.getContents()) { content.transferTo(java.io.OutputStream.nullOutputStream()); }
                        new org.apache.pdfbox.pdfparser.PDFStreamParser(page).parse();
                    }
                }
            } else {
                if (startsWith(bytes, new byte[]{(byte)137,80,78,71,13,10,26,10})) type = "image/png";
                else if (startsWith(bytes, new byte[]{(byte)255,(byte)216,(byte)255})) type = "image/jpeg";
                else throw problem(415, "UPLOAD_FILE_UNSUPPORTED");
                try (var imageInput = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
                    var readers = javax.imageio.ImageIO.getImageReaders(imageInput);
                    if (!readers.hasNext()) throw problem(400, "UPLOAD_FILE_UNREADABLE");
                    var reader = readers.next();
                    try {
                        reader.setInput(imageInput);
                        if ((long)reader.getWidth(0) * reader.getHeight(0) > 40_000_000L)
                            throw problem(400, "UPLOAD_FILE_UNREADABLE");
                        reader.addIIOReadWarningListener((source, warning) -> { throw problem(400, "UPLOAD_FILE_UNREADABLE"); });
                        if (reader.read(0) == null) throw problem(400, "UPLOAD_FILE_UNREADABLE");
                    } finally { reader.dispose(); }
                }
            }
            return new ValidatedUpload(file.getOriginalFilename(), type, bytes);
        } catch (IOException | IllegalArgumentException error) {
            throw problem(400, "UPLOAD_FILE_UNREADABLE");
        }
    }

    private boolean startsWith(byte[] bytes, byte[] signature) {
        return bytes.length >= signature.length && java.util.Arrays.equals(java.util.Arrays.copyOf(bytes, signature.length), signature);
    }

    private org.springframework.web.server.ResponseStatusException problem(int status, String code) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.valueOf(status), code);
    }

    public String uploadFile(MultipartFile file) throws IOException {
        return uploadValidated(validate(file), null).getFilePath();
    }

    private String newStorageName() {
        // Original names are display metadata only, never filesystem input.
        return UUID.randomUUID().toString();
    }

    public void deleteFile(String filePath) throws IOException {
        requireTransaction();
        Path path = storage.resolve(filePath);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cleanup(path, "delete_after_commit"); }
        });
    }

    private void writeTransactional(byte[] bytes, Path path) throws IOException {
        requireTransaction();
        Files.createFile(path); // Claim a new path exclusively; never overwrite an existing file.
        // Register before copying so any later DB/audit/optimistic-lock failure cleans up.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) cleanup(path, "upload_rollback");
            }
        });
        try (var output = Files.newOutputStream(path)) {
            output.write(bytes);
        } catch (IOException | RuntimeException error) {
            cleanup(path, "upload_copy_failure");
            throw error;
        }
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("File mutation requires an active transaction");
        }
    }

    private void cleanup(Path path, String operation) {
        try { Files.deleteIfExists(storage.resolve(path.toString())); }
        catch (IOException | RuntimeException error) {
            // Never turn an already committed DB deletion into a misleading failure response.
            log.error("file_cleanup_failed operation={} storedFile={} exceptionType={}",
                    operation, path.getFileName(), error.getClass().getSimpleName());
        }
    }
    public AiLearningFileEntity uploadFile(MultipartFile file, AiLearningModuleEntity module) throws IOException {
        checkFileCount(module.getFiles().size(), 1);
        return uploadValidated(validate(file), module);
    }

    public AiLearningFileEntity uploadValidated(ValidatedUpload file, AiLearningModuleEntity module) throws IOException {
        String storedFileName = newStorageName();
        Path filePath = storage.resolve(storedFileName);
        writeTransactional(file.bytes(), filePath);
        AiLearningFileEntity entity = new AiLearningFileEntity();
        entity.setModule(module);
        entity.setOriginalFileName(file.originalName());
        entity.setStoredFileName(storedFileName);
        entity.setFilePath(storage.storedPath(filePath));
        entity.setFileType(file.mediaType());
        return entity;
    }
}
