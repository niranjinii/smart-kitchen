package com.sk.smart_kitchen.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.url-prefix:/uploads}")
    private String uploadUrlPrefix;

    public String storeImage(MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            return null;
        }

        String contentType = imageFile.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed.");
        }

        String extension = extensionFromOriginalName(imageFile.getOriginalFilename());
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported image type. Allowed: jpg, jpeg, png, gif, webp.");
        }

        String fileName = UUID.randomUUID() + "." + extension;

        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            Path targetPath = uploadPath.resolve(fileName).normalize();
            Files.copy(imageFile.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to store image file.", ex);
        }

        String normalizedPrefix = normalizeUrlPrefix(uploadUrlPrefix);
        return normalizedPrefix + "/" + fileName;
    }

    private String normalizeUrlPrefix(String prefix) {
        String value = prefix == null ? "/uploads" : prefix.trim();
        if (value.isEmpty()) {
            value = "/uploads";
        }

        value = value.replace('\\', '/');
        while (value.contains("//")) {
            value = value.replace("//", "/");
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isEmpty() ? "/uploads" : value;
    }

    private String extensionFromOriginalName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return null;
        }

        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return null;
        }

        return originalFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
