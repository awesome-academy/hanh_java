package com.psms.dto.response;

import com.psms.enums.ValidationStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationDocumentResponse {

    private Long id;
    private String fileName;
    private String fileType;
    private long fileSize;
    private String documentType;
    private Long uploadedById;
    private String uploadedByName;
    private LocalDateTime uploadedAt;
    private boolean isResponse;
    private ValidationStatus validationStatus;

    /** Download URL — được populate trong quá trình build/mapping response. */
    private String downloadUrl;

    public String getValidationLabel() {
        return validationStatus == null ? null : validationStatus.getLabel();
    }

    public String getValidationBadgeClass() {
        return validationStatus == null ? "" : validationStatus.getBadgeClass();
    }

    /** Format file size cho display: "1.2 MB", "500 KB". */
    public String getFileSizeDisplay() {
        if (fileSize >= 1_048_576) {
            return String.format("%.1f MB", fileSize / 1_048_576.0);
        } else if (fileSize >= 1024) {
            return String.format("%.0f KB", fileSize / 1024.0);
        }
        return fileSize + " B";
    }
}

