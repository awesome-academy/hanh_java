package com.psms.service;

import com.psms.exception.BusinessException;
import com.psms.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import com.psms.config.FileStorageProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Lưu file trên local filesystem — phù hợp cho môi trường dev và single-server.
 *
 * <p>Path traversal protection: mọi path được resolve qua uploadRoot.normalize()
 * và verify bắt đầu bằng uploadRoot trước khi đọc/ghi.
 *
 * <p>Filename collision prevention: prefix UUID trước tên gốc (đã sanitize).
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {


    private final FileStorageProperties fileProps;
    // Constructor tường minh thay cho @RequiredArgsConstructor
    public LocalFileStorageService(FileStorageProperties fileProps) {
        this.fileProps = fileProps;
    }

    private Path uploadRoot;

    @PostConstruct
    public void init() {
        String dir = fileProps.uploadDir() != null ? fileProps.uploadDir() : "uploads";
        uploadRoot = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRoot);
            log.info("File storage initialized at: {}", uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể tạo thư mục upload: " + uploadRoot, e);
        }
    }

    @Override
    public String store(MultipartFile file, Long applicationId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File không được để trống");
        }

        // Validate size
        if (file.getSize() > fileProps.maxSizeBytes()) {
            throw new BusinessException("File vượt quá dung lượng cho phép (tối đa "
                    + fileProps.maxSizeMb() + " MB): "
                    + file.getOriginalFilename());
        }

        // Validate extension
        String ext = extractExtension(file.getOriginalFilename());
        if (!fileProps.isExtensionAllowed(ext)) {
            throw new BusinessException(
                    "Định dạng file không được hỗ trợ: ." + ext
                    + ". Chỉ chấp nhận: " + fileProps.allowedExtensionsDisplay());
        }

        // Sanitize filename: bỏ path component, giữ lại tên gốc để UX tốt
        String safeName = sanitizeFilename(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + "_" + safeName;

        // Tổ chức theo applicationId để dễ quản lý
        Path appDir = uploadRoot.resolve(String.valueOf(applicationId));
        try {
            Files.createDirectories(appDir);
            Path target = appDir.resolve(storedName);

            // Path traversal check: target phải nằm trong uploadRoot
            if (!target.normalize().startsWith(uploadRoot)) {
                throw new BusinessException("Tên file không hợp lệ");
            }

            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Stored file: {}/{}", applicationId, storedName);

            // Trả về relative path — không expose absolute path ra ngoài
            return applicationId + "/" + storedName;

        } catch (IOException e) {
            throw new BusinessException("Không thể lưu file: " + e.getMessage());
        }
    }

    @Override
    public Resource load(String relativePath) {
        Path filePath = uploadRoot.resolve(relativePath).normalize();

        // Path traversal check
        if (!filePath.startsWith(uploadRoot)) {
            throw new ResourceNotFoundException("File không tồn tại: " + relativePath);
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("File không tồn tại: " + relativePath);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("File không tồn tại: " + relativePath);
        }
    }

    @Override
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        Path filePath = uploadRoot.resolve(relativePath).normalize();
        if (!filePath.startsWith(uploadRoot)) return; // silent reject invalid path
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Không thể xóa file: {}", relativePath, e);
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────

    /** Trích extension, lowercase. Trả "bin" nếu không có extension. */
    private String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) return "bin";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "bin";
    }

    /**
     * Loại bỏ path component, ký tự đặc biệt nguy hiểm.
     * VD: "../../etc/passwd.pdf" → "passwd.pdf"
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) return "file";
        // Chỉ lấy phần sau dấu / hoặc \ cuối cùng
        String name = Paths.get(filename).getFileName().toString();
        // Thay thế ký tự không phải alphanumeric/dot/dash/underscore bằng _
        return name.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
    }
}

