package org.example.cardify.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;

public final class ImageDataUrlConverter {
    private ImageDataUrlConverter() {
    }

    public static boolean looksLikeImagePath(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp");
    }

    public static String toDataUrl(Path imagePath) {
        if (!Files.exists(imagePath)) {
            return imagePath.toString();
        }
        try {
            byte[] bytes = Files.readAllBytes(imagePath);
            String mediaType = Files.probeContentType(imagePath);
            if (mediaType == null || mediaType.isBlank()) {
                mediaType = guessMediaType(imagePath);
            }
            return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read image file: " + imagePath, exception);
        }
    }

    private static String guessMediaType(Path imagePath) {
        String name = imagePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }
}
