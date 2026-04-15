package com.psms.dto.response;

import lombok.*;

/**
 * Response DTO cho admin quản lý cán bộ.
 * Bao gồm thông tin cán bộ + workload (số hồ sơ đang xử lý) + workload CSS class.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminStaffResponse {

    private Long staffId;
    private Long userId;
    private String staffCode;
    private String fullName;
    private String email;
    private String phone;
    private Long departmentId;
    private String departmentName;
    private String position;
    private boolean available;

    /** Số hồ sơ đang xử lý (SUBMITTED/RECEIVED/PROCESSING/ADDITIONAL_REQUIRED) */
    private long activeApplicationCount;

    /**
     * CSS pill class cho workload indicator:
     * <ul>
     *   <li>p-green : 0–4 hồ sơ</li>
     *   <li>p-amber : 5–7 hồ sơ</li>
     *   <li>p-red   : ≥ 8 hồ sơ</li>
     * </ul>
     */
    public String getWorkloadClass() {
        if (activeApplicationCount >= 8) return "p-red";
        if (activeApplicationCount >= 5) return "p-amber";
        return "p-green";
    }
}

