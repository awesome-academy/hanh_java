package com.psms.controller.client;

import com.psms.dto.response.ApiResponse;
import com.psms.dto.response.NotificationResponse;
import com.psms.entity.User;
import com.psms.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller cho thông báo in-app của citizen.
 *
 * <p>Business rules
 * <ul>
 *   <li>Citizen chỉ xem được notification của mình</li>
 *   <li>markAsRead kiểm tra ownership để tránh IDOR</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/client/notifications")
@RequiredArgsConstructor
@Tag(name = "Client - Notifications", description = "Thông báo in-app cho công dân")
public class ClientNotificationController {

    private final NotificationService notificationService;
    private static final String DEFAULT_PAGE_SIZE_STR = "20";

    @Operation(
        summary = "Danh sách thông báo",
        description = "Phân trang 20/trang. Filter: isRead=true/false/null(tất cả)"
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_STR) int size) {

        if (page < 0) throw new IllegalArgumentException("'page' phải >= 0, nhận: " + page);
        if (size < 1 || size > 50) throw new IllegalArgumentException("'size' phải trong khoảng 1–50, nhận: " + size);

        return ResponseEntity.ok(ApiResponse.success(
                notificationService.findByUser(user.getId(), isRead, page, size)));
    }

    @Operation(
        summary = "Đếm thông báo chưa đọc",
        description = "Dùng cho badge trên topbar. Trả về {\"count\": N}"
    )
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(
            @AuthenticationPrincipal User user) {
        long count = notificationService.countUnread(user.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    @Operation(
        summary = "Đánh dấu 1 thông báo đã đọc",
        description = "Kiểm tra ownership (chống IDOR). Idempotent: gọi nhiều lần vẫn OK"
    )
    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        notificationService.markAsRead(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(
        summary = "Đánh dấu tất cả thông báo đã đọc",
        description = "Bulk update toàn bộ unread notifications của user hiện tại"
    )
    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal User user) {
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
