package com.psms.controller.web;

import com.psms.dto.response.NotificationResponse;
import com.psms.entity.User;
import com.psms.service.NotificationService;
import com.psms.util.PaginationInfo;
import com.psms.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * MVC controller — render Thymeleaf pages cho Notifications.
 *
 * <p>Tất cả route yêu cầu role CITIZEN.
 * PRG pattern: POST xử lý xong → redirect GET + flash message.
 */
@Controller
@RequiredArgsConstructor
@PreAuthorize("hasRole('CITIZEN')")
public class NotificationViewController {
    private final NotificationService notificationService;

    private static final String DEFAULT_PAGE_SIZE_STR = "20";

    // ─── GET /notifications ────────────────────────────────────────────────

    /**
     * GET /notifications/unread-count — đếm badge cho topbar JS polling.
     *
     * <p>Dùng MVC session auth thay vì REST /api/** để JS trong Thymeleaf page
     * không cần gửi JWT Bearer. Session cookie được gửi tự động bởi browser.
     *
     * <p>Trả JSON đơn giản {@code {"count": N}} — không wrap ApiResponse
     * vì đây là internal MVC endpoint, không phải public REST API.
     */
    @GetMapping("/notifications/unread-count")
    @ResponseBody
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal User user) {
        long count = notificationService.countUnread(user.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/notifications")
    public String showNotifications(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_STR) int size,
            Model model) {

        // Giới hạn page không âm và size trong khoảng 1..50 để tránh PageRequest.of(...) ném lỗi
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, 50);

        Page<NotificationResponse> notifications =
                notificationService.findByUser(user.getId(), isRead, safePage, safeSize);

        PaginationInfo pageInfo = PaginationUtils.calculate(notifications);

        model.addAttribute("notifications", notifications);
        model.addAttribute("isRead", isRead);

        model.addAttribute("pageStart", pageInfo.pageStart());
        model.addAttribute("pageEnd", pageInfo.pageEnd());
        model.addAttribute("displayFrom", pageInfo.displayFrom());
        model.addAttribute("displayTo", pageInfo.displayTo());
        model.addAttribute("activeNav", "notifications");
        return "client/notifications";
    }

    // ─── POST /notifications/{id}/read ────────────────────────────────────

    @PostMapping("/notifications/{id}/read")
    public String markAsRead(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam(required = false) Long applicationId,
            RedirectAttributes ra) {
        try {
            notificationService.markAsRead(id, user.getId());
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/notifications";
        }
        // Redirect đến hồ sơ liên quan nếu có, ngược lại về danh sách
        if (applicationId != null) {
            return "redirect:/applications/" + applicationId;
        }
        return "redirect:/notifications";
    }

    // ─── POST /notifications/read-all ─────────────────────────────────────

    @PostMapping("/notifications/read-all")
    public String markAllAsRead(@AuthenticationPrincipal User user, RedirectAttributes ra) {
        notificationService.markAllAsRead(user.getId());
        ra.addFlashAttribute("success", "Đã đánh dấu tất cả thông báo là đã đọc.");
        return "redirect:/notifications";
    }
}

