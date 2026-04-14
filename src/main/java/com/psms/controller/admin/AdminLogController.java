package com.psms.controller.admin;

import com.psms.dto.response.ActivityLogResponse;
import com.psms.dto.response.ApiResponse;
import com.psms.service.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * REST API cho admin activity log.
 *
 * <p>Access rules:
 * <ul>
 *   <li>GET /api/admin/logs — MANAGER + SUPER_ADMIN xem được</li>
 *   <li>DELETE /api/admin/logs/purge — chỉ SUPER_ADMIN</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
@Tag(name = "Admin Activity Log", description = "Nhật ký hoạt động hệ thống")
public class AdminLogController {

    private final ActivityLogService activityLogService;

    private static final String DEFAULT_PAGE_SIZE_STR = "20";

    /**
     * Lấy danh sách activity logs với filter đa chiều, phân trang.
     *
     * <p><b>Input:</b>
     * <ul>
     *   <li>userId (Long, optional) — lọc theo người thực hiện</li>
     *   <li>action (String, optional) — lọc theo action type (LOGIN, UPDATE_STATUS, ...)</li>
     *   <li>from / to (ISO datetime, optional) — khoảng thời gian</li>
     * </ul>
     *
     * <p><b>Output:</b> Page của {@link ActivityLogResponse}, sắp xếp createdAt DESC.
     */
    @Operation(
        summary = "Danh sách activity logs",
        description = "Filter theo userId, action type, date range. Phân trang 20 records/trang."
    )
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<ActivityLogResponse>>> getLogs(
            @Parameter(description = "Lọc theo user ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "Lọc theo action (LOGIN, UPDATE_STATUS, ...)") @RequestParam(required = false) String action,
            @Parameter(description = "Từ ngày (ISO-8601)") @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Đến ngày (ISO-8601)") @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_STR) int size) {

        if (page < 0) throw new IllegalArgumentException("'page' phải >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("'size' phải trong khoảng 1-100");

        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' (" + from + ") không được sau 'to' (" + to + ")");
        }

        Page<ActivityLogResponse> result = activityLogService.findLogs(userId, action, from, to, page, size);
        return ResponseEntity.ok(ApiResponse.success("OK", result));
    }

    /**
     * Xóa activity logs cũ hơn N ngày — chỉ SUPER_ADMIN.
     *
     * <p><b>Business rule:</b> Chỉ xóa log cũ hơn {@code days} ngày để bảo vệ audit trail gần đây.
     * Default = 90 ngày nếu không truyền tham số.
     *
     * <p><b>Output:</b> Số bản ghi đã xóa.
     */
    @Operation(
        summary = "Xóa log cũ (SUPER_ADMIN only)",
        description = "Xóa tất cả activity logs cũ hơn `days` ngày. Default = 90. Hành động không thể hoàn tác."
    )
    @DeleteMapping("/purge")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> purge(
            @Parameter(description = "Xóa log cũ hơn N ngày") @RequestParam(defaultValue = "90") int days) {
        int deleted = activityLogService.purgeOlderThan(days);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã xóa " + deleted + " bản ghi log cũ hơn " + days + " ngày",
                Map.of("deleted", deleted)));
    }
}

