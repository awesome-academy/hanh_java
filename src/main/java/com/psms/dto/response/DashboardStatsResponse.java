package com.psms.dto.response;

import lombok.*;

/**
 * 4 KPI cards hiển thị trên Admin Dashboard.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsResponse {

    /** Tổng số hồ sơ trong hệ thống */
    private long totalApplications;

    /** Đang xử lý (RECEIVED + PROCESSING + ADDITIONAL_REQUIRED) */
    private long processingApplications;

    /** Đã hoàn thành (APPROVED + REJECTED) */
    private long completedApplications;

    /** Hồ sơ quá hạn (processing_deadline < NOW() và chưa hoàn thành) */
    private long overdueApplications;
}

