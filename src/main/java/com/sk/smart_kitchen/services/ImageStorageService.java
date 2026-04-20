package com.sk.smart_kitchen.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ImageStorageService {

    // PRESERVED: Your original security constraints
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final Cloudinary cloudinary;

    public ImageStorageService(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret));
    }

    public String storeImage(MultipartFile imageFile) {
        // PRESERVED: Your original validation logic
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

        // NEW: Send the validated file directly to Cloudinary instead of the local hard drive
        try {
            Map uploadResult = cloudinary.uploader().upload(
                imageFile.getBytes(), 
                ObjectUtils.asMap("folder", "smart_kitchen/recipes")
            );
            return uploadResult.get("secure_url").toString();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to upload image to Cloudinary.", ex);
        }
    }

    // PRESERVED: Your original helper method
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