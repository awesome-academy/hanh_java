package com.psms.entity;

import com.psms.entity.base.LongBaseEntity;
import com.psms.enums.ValidationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "application_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationDocument extends LongBaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** Đường dẫn lưu trữ tương đối (relative to upload root) */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "document_type", length = 50)
    private String documentType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    /** true = tài liệu phản hồi từ cán bộ, false = tài liệu citizen nộp. */
    @Column(name = "is_response", nullable = false)
    @Builder.Default
    private boolean isResponse = false;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "validation_status", nullable = false, length = 10)
    @Builder.Default
    private ValidationStatus validationStatus = ValidationStatus.PENDING;

    /**
     * Soft delete flag — true = đã xóa, false = còn hoạt động.
     * File vật lý vẫn giữ nguyên để đảm bảo audit trail
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;
}

