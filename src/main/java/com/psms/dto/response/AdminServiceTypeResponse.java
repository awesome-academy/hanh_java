package com.psms.dto.response;

import lombok.*;

import java.math.BigDecimal;

/**
 * Response DTO cho admin quản lý dịch vụ công.
 * Bao gồm tất cả fields của ServiceTypeDetailResponse + activeApplicationCount (workload).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminServiceTypeResponse {

    private Long id;
    private String code;
    private String name;
    private Integer categoryId;
    private String categoryName;
    private Long departmentId;
    private String departmentName;
    private String description;
    private String requirements;
    private short processingTimeDays;
    private BigDecimal fee;
    private String feeDescription;
    private boolean active;

    /** Số hồ sơ đang xử lý (SUBMITTED/RECEIVED/PROCESSING/ADDITIONAL_REQUIRED) */
    private long activeApplicationCount;
}

