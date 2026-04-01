package com.psms.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Mở rộng {@link LongBaseEntity} với {@code created_at} và {@code updated_at}.
 *
 * <p>{@code @CreationTimestamp} / {@code @UpdateTimestamp}: Hibernate tự set khi INSERT/UPDATE,
 * không cần viết {@code @PrePersist} thủ công.
 * {@code updatable = false} trên {@code created_at} đảm bảo giá trị không bao giờ bị ghi đè.
 *
 * <p><b>Entity sử dụng:</b> ServiceType, Application, ApplicationStatusHistory.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditableLongEntity extends LongBaseEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
