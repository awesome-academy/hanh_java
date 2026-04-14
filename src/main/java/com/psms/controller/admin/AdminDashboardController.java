package com.psms.controller.admin;

import com.psms.dto.response.AdminApplicationResponse;
import com.psms.dto.response.ApiResponse;
import com.psms.dto.response.DashboardChartResponse;
import com.psms.dto.response.DashboardStatsResponse;
import com.psms.service.AdminApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import java.util.List;

/**
 * REST API cho admin dashboard.
 * Chi STAFF / MANAGER / SUPER_ADMIN truy cap duoc.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STAFF','MANAGER','SUPER_ADMIN')")
@Tag(name = "Admin Dashboard", description = "KPI va thong ke cho dashboard quan tri")
public class AdminDashboardController {

    private final AdminApplicationService adminApplicationService;

    @Operation(
        summary = "Lay 4 KPI cards",
        description = """
            Tra ve 4 chi so tong quan:
            - totalApplications: tong ho so
            - processingApplications: RECEIVED + PROCESSING + ADDITIONAL_REQUIRED
            - completedApplications: APPROVED + REJECTED
            - overdueApplications: qua processingDeadline chua hoan thanh
            """
    )
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.success("OK", adminApplicationService.getDashboardStats()));
    }

    @Operation(
        summary = "10 ho so moi nhat can xu ly",
        description = "Ho so co status SUBMITTED hoac RECEIVED, sap xep moi nhat truoc."
    )
    @GetMapping("/recent-applications")
    public ResponseEntity<ApiResponse<List<AdminApplicationResponse>>> getRecentApplications() {
        return ResponseEntity.ok(ApiResponse.success("OK", adminApplicationService.getRecentPendingApplications()));
    }

    @Operation(
        summary = "Phân bố hồ sơ theo lĩnh vực",
        description = "Trả về danh sách {label, count, percent} sắp xếp theo count DESC. Dùng cho CSS bar chart."
    )
    @GetMapping("/by-category")
    public ResponseEntity<ApiResponse<List<DashboardChartResponse>>> getByCategory() {
        return ResponseEntity.ok(ApiResponse.success("OK", adminApplicationService.getByCategory()));
    }

    @Operation(
        summary = "Phân bố hồ sơ theo trạng thái",
        description = "Trả về danh sách {label, count, percent, cssClass} cho từng trạng thái. Dùng cho status donut chart."
    )
    @GetMapping("/by-status")
    public ResponseEntity<ApiResponse<List<DashboardChartResponse>>> getByStatus() {
        return ResponseEntity.ok(ApiResponse.success("OK", adminApplicationService.getByStatus()));
    }
}

