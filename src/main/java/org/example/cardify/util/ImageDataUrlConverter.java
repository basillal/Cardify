package org.example.cardify.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;

import javax.imageio.ImageIO;

public final class ImageDataUrlConverter {
    private static final int QR_SIZE = 320;

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

    public static String toQrCodeDataUrl(String content) {
        String value = content == null ? "" : content.trim();
        if (value.isBlank()) {
            return "";
        }

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(value, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (WriterException | IOException exception) {
            throw new IllegalStateException("Unable to generate QR code", exception);
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
