package com.funccrypto.ridedispatch.brand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;

import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class BrandLogoStorage {

    private static final long MAX_BYTES = 2 * 1024 * 1024;

    private final Path root;

    public BrandLogoStorage(
            @Value("${app.brand.logo-upload-dir:./data/brand}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public StoredLogo store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("BRAND_LOGO_EMPTY", "Logo 文件不能为空");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("BRAND_LOGO_TOO_LARGE", "Logo 文件不能超过 2 MB");
        }
        String contentType = normalizeContentType(file.getContentType());
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException("BRAND_LOGO_READ_FAILED", "Logo 文件读取失败");
        }
        if (bytes.length == 0 || bytes.length > MAX_BYTES || !isSupportedImage(contentType, bytes)) {
            throw new BusinessException("BRAND_LOGO_INVALID", "仅支持有效的 PNG、JPEG 或 WebP 图片");
        }

        String extension = extensionFor(contentType);
        String filename = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(root);
            Files.write(root.resolve(filename), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new BusinessException("BRAND_LOGO_WRITE_FAILED", "Logo 文件保存失败");
        }
        return new StoredLogo(filename, "/api/v1/public/brand/logo/" + filename, contentType);
    }

    public StoredResource load(String filename) {
        Path candidate;
        try {
            candidate = filename == null ? null : Path.of(filename);
        } catch (RuntimeException exception) {
            candidate = null;
        }
        if (candidate == null || filename.isBlank() || !filename.equals(candidate.getFileName().toString())) {
            throw new BusinessException("BRAND_LOGO_NOT_FOUND", "Logo 文件不存在");
        }
        Path file = root.resolve(filename).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new BusinessException("BRAND_LOGO_NOT_FOUND", "Logo 文件不存在");
        }
        try {
            Resource resource = new UrlResource(file.toUri());
            return new StoredResource(resource, mediaTypeFor(filename));
        } catch (IOException exception) {
            throw new BusinessException("BRAND_LOGO_READ_FAILED", "Logo 文件读取失败");
        }
    }

    private static String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
    }

    private static boolean isSupportedImage(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "image/png" -> bytes.length >= 8
                    && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                    && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A;
            case "image/jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> ".png";
        };
    }

    private static MediaType mediaTypeFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_PNG;
    }

    public record StoredLogo(String filename, String url, String contentType) {
    }

    public record StoredResource(Resource resource, MediaType mediaType) {
    }
}
