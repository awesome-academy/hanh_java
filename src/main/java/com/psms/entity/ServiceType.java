package com.psms.entity;

import com.psms.entity.base.AuditableLongEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "service_types")
@Getter
@Setter
@NoArgsConstructor
public class ServiceType extends AuditableLongEntity {

    @Column(name = "code", nullable = false, length = 30, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ServiceCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "requirements", columnDefinition = "TEXT")
    private String requirements;

    @Column(name = "processing_time_days", nullable = false)
    private short processingTimeDays = 5;

    @Column(name = "fee", nullable = false, precision = 12, scale = 0)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(name = "fee_description", length = 255)
    private String feeDescription;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
}

