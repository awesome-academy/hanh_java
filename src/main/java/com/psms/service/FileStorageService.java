package com.psms.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Interface lưu trữ file — dễ swap sang S3 hoặc cloud storage sau này.
 *
 * <p>Local implementation: LocalFileStorageService.
 */
public interface FileStorageService {

    /**
     * Lưu file vào storage, trả về đường dẫn tương đối (relative path).
     */
    String store(MultipartFile file, Long applicationId);

    /**
     * Load file theo relative path để serve cho client.
     */
    Resource load(String relativePath);

    /**
     * Xóa file khỏi storage. Silent nếu file không tồn tại.
     */
    void delete(String relativePath);
}


