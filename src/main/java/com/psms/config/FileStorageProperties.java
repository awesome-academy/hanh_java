
// File: com.psms.config.FileStorageProperties.java
package com.psms.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

/**
 * Cấu hình file upload — bind từ application.yml prefix "file".
 *
 * application.yml:
 * file:
 *   max-size-bytes: 10485760
 *   allowed-extensions: [pdf, jpg, jpeg, png, docx]
 *   upload-dir: ./uploads
 */
@ConfigurationProperties(prefix = "file")
@Validated
public record FileStorageProperties(

    @NotNull(message = "max-size-bytes không được null")
    @Positive(message = "max-size-bytes phải lớn hơn 0")
    Long maxSizeBytes,

    @NotEmpty(message = "allowed-extensions không được rỗng")
    Set<String> allowedExtensions,

    @NotNull(message = "upload-dir không được null")
    String uploadDir

) {
    /** Trả về max size theo MB — dùng trong error message. */
    public long maxSizeMb() {
        return maxSizeBytes / 1_048_576;
    }

    /** Kiểm tra extension có được phép không (case-insensitive). */
    public boolean isExtensionAllowed(String extension) {
        if (extension == null || extension.isBlank()) return false;
        return allowedExtensions.contains(extension.toLowerCase());
    }

    /** Trả về danh sách extension dạng chuỗi — dùng trong error message. */
    public String allowedExtensionsDisplay() {
        return String.join(", ", allowedExtensions);
    }
}
