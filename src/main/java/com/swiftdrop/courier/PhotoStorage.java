package com.swiftdrop.courier;

import com.swiftdrop.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Saves proof-of-delivery photos to a folder (a Docker volume in deployment).
 * Only JPEG/PNG up to 5 MB. The file name is random, never the name the user sent,
 * so a malicious name like "../../etc/passwd" cannot be used.
 */
@Component
public class PhotoStorage {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png");

    private final Path root;

    public PhotoStorage(@Value("${swiftdrop.upload-dir}") String uploadDir) throws IOException {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    /** Returns the stored file name (saved in delivery_proofs.photo_path). */
    public String save(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return null;
        }
        String extension = ALLOWED.get(photo.getContentType());
        if (extension == null) {
            throw ApiException.badRequest("Photo must be a JPEG or PNG image");
        }
        if (photo.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("Photo must be smaller than 5 MB");
        }
        String fileName = UUID.randomUUID() + extension;
        try (InputStream in = photo.getInputStream()) {
            Files.copy(in, root.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save photo", e);
        }
        return fileName;
    }

    public byte[] load(String fileName) {
        Path file = root.resolve(fileName).normalize();
        if (!file.startsWith(root) || !Files.exists(file)) {
            throw ApiException.notFound("Photo not found");
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read photo", e);
        }
    }
}
