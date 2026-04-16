package com.psms.entity;

import com.psms.entity.base.LongBaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Nhật ký hoạt động toàn hệ thống — audit log.
 */
@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog extends LongBaseEntity {

    /**
     * Người thực hiện hành động. Nullable — NULL = hành động bởi hệ thống (scheduler...).
     * ON DELETE SET NULL để không mất log khi user bị xóa (audit trail phải bền vững).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Loại hành động: LOGIN, SUBMIT_APP, UPDATE_STATUS, CREATE_USER, ... */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    /** Loại entity bị tác động: applications | users | service_types | departments | staff */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    /** ID của entity bị tác động (String để hỗ trợ mọi kiểu ID). */
    @Column(name = "entity_id", length = 50)
    private String entityId;

    /** Mô tả hành động — bắt buộc. */
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /** IP address của client (IPv4 hoặc IPv6). Null nếu internal job. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** User-Agent header từ request. Null nếu internal job. */
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

